package it.pagopa.pn.papertracker.service.handler_step;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerNotFoundException;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.api.PcRetryApi;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PcRetryResponse;
import it.pagopa.pn.papertracker.mapper.PaperTrackingsErrorsMapper;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ErrorCategory;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ErrorType;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.FlowThrow;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.mapper.PaperTrackingsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RetrySender implements HandlerStep {

    private final PaperTrackingsDAO paperTrackingsDAO;
    private final PnPaperTrackerConfigs pnPaperTrackerConfigs;
    private final PcRetryApi pcRetryApi;

    @Override
    public Mono<Void> execute(HandlerContext context) {
        return pcRetryApi.getPcRetry(context.getPaperTrackings().getTrackingId())
                .onErrorResume(throwable -> {
                    if (throwable instanceof WebClientResponseException webEx && webEx.getStatusCode() == HttpStatus.NOT_FOUND) {
                        return Mono.error(new PnPaperTrackerNotFoundException("RequestId not found", webEx.getMessage()));
                    }
                    return Mono.error(throwable);
                })
                .flatMap(pcRetryResponse -> {
                    if (Boolean.TRUE.equals(pcRetryResponse.getRetryFound())) {
                        return paperTrackingsDAO.updateItem(context.getPaperTrackings().getTrackingId(), getPaperTrackingsPcretry(pcRetryResponse))
                                .flatMap(paperTrackings -> paperTrackingsDAO.putIfAbsent(PaperTrackingsMapper.toPaperTrackings(pcRetryResponse, pnPaperTrackerConfigs.getPaperTrackingsTtlDuration(), context.getPaperTrackings().getProductType())));
                    } else {
                        throw new PnPaperTrackerValidationException(String.format("Retry not found for trackingId: %s ", context.getPaperTrackings().getTrackingId()),
                                PaperTrackingsErrorsMapper.buildPaperTrackingsError(context.getPaperTrackings(),
                                        List.of(context.getPaperProgressStatusEvent().getStatusCode()),
                                        ErrorCategory.MAX_RETRY_REACHED_ERROR,
                                        null,
                                        "Retry not found for trackingId: " + context.getPaperTrackings().getTrackingId(),
                                        FlowThrow.RETRY_PHASE,
                                        ErrorType.ERROR));
                    }
                })
                .then();
    }

    private PaperTrackings getPaperTrackingsPcretry(PcRetryResponse pcRetryResponse) {
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setNextRequestIdPcretry(pcRetryResponse.getRequestId());
        return paperTrackings;
    }

}