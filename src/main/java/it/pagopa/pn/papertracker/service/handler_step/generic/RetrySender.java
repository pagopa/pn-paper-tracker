package it.pagopa.pn.papertracker.service.handler_step.generic;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.exception.PaperTrackerExceptionHandler;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PcRetryResponse;
import it.pagopa.pn.papertracker.mapper.PaperTrackingsErrorsMapper;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.middleware.msclient.PaperChannelClient;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import it.pagopa.pn.papertracker.mapper.PaperTrackingsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RetrySender implements HandlerStep {

    private final PaperTrackingsDAO paperTrackingsDAO;
    private final PnPaperTrackerConfigs pnPaperTrackerConfigs;
    private final PaperChannelClient paperChannelClient;
    private final PaperTrackerExceptionHandler paperTrackerExceptionHandler;

    /**
     * Step di invio della richiesta di retry al Paper Channel.
     * In caso di esito positivo, aggiorna lo stato del paper tracking a "IN_RETRY".
     * In caso di errore, gestisce l'eccezione tramite il PaperTrackerExceptionHandler.
     * @param context Contesto contenente le informazioni necessarie per l'elaborazione dell'evento.
     * @return Mono(Void)
     */
    @Override
    public Mono<Void> execute(HandlerContext context) {
        return paperChannelClient.getPcRetry(context.getPaperTrackings().getTrackingId())
                .doOnError(throwable -> log.error("Error retrieving retry for trackingId: {}", context.getPaperTrackings().getTrackingId(), throwable))
                .flatMap(pcRetryResponse -> {
                    if (Boolean.TRUE.equals(pcRetryResponse.getRetryFound())) {
                        return paperTrackingsDAO.putIfAbsent(PaperTrackingsMapper.toPaperTrackings(pcRetryResponse, pnPaperTrackerConfigs.getPaperTrackingsTtlDuration(), context.getPaperTrackings().getProductType(), context.getPaperTrackings().getAttemptId()))
                                .doOnNext(paperTrackings -> {
                                    PaperTrackings paperTrackingsToUpdate = getPaperTrackingsPcretry(pcRetryResponse);
                                    context.setPaperTrackings(paperTrackingsToUpdate);
                                });
                    } else {
                        return paperTrackerExceptionHandler.handleRetryError(PaperTrackingsErrorsMapper.buildPaperTrackingsError(context.getPaperTrackings(),
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
        paperTrackings.setState(PaperTrackingsState.DONE);
        paperTrackings.setNextRequestIdPcretry(pcRetryResponse.getRequestId());
        return paperTrackings;
    }

}