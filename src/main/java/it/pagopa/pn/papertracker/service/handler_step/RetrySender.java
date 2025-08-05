package it.pagopa.pn.papertracker.service.handler_step;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerNotFoundException;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.api.PcRetryApi;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PcRetryResponse;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsErrorsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.mapper.PaperTrackingsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class RetrySender implements HandlerStep {

    private final PaperTrackingsDAO paperTrackingsDAO;
    private final PaperTrackingsErrorsDAO paperTrackingsErrorsDAO;
    private final PnPaperTrackerConfigs pnPaperTrackerConfigs;
    private final PcRetryApi pcRetryApi;

    @Override
    public Mono<Void> execute(HandlerContext context) {
        return pcRetryApi.getPcRetry(context.getPaperTrackings().getRequestId())
                .onErrorMap(
                        ex -> ex instanceof RuntimeException,
                        ex -> new PnPaperTrackerNotFoundException("RequestId not found", ex.getMessage())
                )
                .flatMap(pcRetryResponse -> {
                    if (Boolean.TRUE.equals(pcRetryResponse.getRetryFound())) {
                        paperTrackingsDAO.updateItem(context.getPaperTrackings().getRequestId(), getPaperTrackingsPcretry(pcRetryResponse));
                        paperTrackingsDAO.putIfAbsent(
                                PaperTrackingsMapper.toPaperTrackings(
                                        pcRetryResponse,
                                        pnPaperTrackerConfigs.getPaperTrackingsTtlDuration(),
                                        context.getPaperTrackings().getProductType()
                                )
                        );
                    } else {
                        paperTrackingsErrorsDAO.insertError(getPaperTrackingsErrors(pcRetryResponse, context));
                    }
                    return Mono.empty();
                });
    }

    private PaperTrackings getPaperTrackingsPcretry(PcRetryResponse pcRetryResponse) {
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setNextRequestIdPcretry(pcRetryResponse.getRequestId());
        return paperTrackings;
    }

    private PaperTrackingsErrors getPaperTrackingsErrors(PcRetryResponse pcRetryResponse, HandlerContext context) {
        PaperTrackingsErrors paperTrackingsErrors = new PaperTrackingsErrors();
        paperTrackingsErrors.setRequestId(pcRetryResponse.getRequestId());
        paperTrackingsErrors.setCreated(Instant.now());
        paperTrackingsErrors.setErrorCategory(ErrorCategory.UNKNOWN);
        ErrorDetails errorDetails = new ErrorDetails();
        errorDetails.setMessage(""); //TODO
        paperTrackingsErrors.setDetails(errorDetails);
        paperTrackingsErrors.setFlowThrow(null); //TODO
        paperTrackingsErrors.setEventThrow(context.getPaperProgressStatusEvent().getStatusCode());
        paperTrackingsErrors.setProductType(context.getPaperTrackings().getProductType());
        paperTrackingsErrors.setType(ErrorType.ERROR);
        return paperTrackingsErrors;
    }

}