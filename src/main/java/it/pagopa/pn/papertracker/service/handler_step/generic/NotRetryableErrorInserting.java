package it.pagopa.pn.papertracker.service.handler_step.generic;

import it.pagopa.pn.papertracker.exception.PaperTrackerExceptionHandler;
import it.pagopa.pn.papertracker.mapper.PaperTrackingsErrorsMapper;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.model.EventStatusCodeEnum;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotRetryableErrorInserting implements HandlerStep {

    private final PaperTrackerExceptionHandler paperTrackerExceptionHandler;

    /**
     * Step che costruisce un oggetto `PaperTrackingsErrors`, relativamente ad un evento notRetryable,
     * utilizzando i dati forniti nel contesto
     * e delega la gestione dell'errore al rispettivo handler.
     *
     * @param context Contesto contenente le informazioni necessarie per l'elaborazione dell'evento.
     * @return {@link Mono<Void>}
     */
    @Override
    public Mono<Void> execute(HandlerContext context) {
        String statusCode = context.getPaperProgressStatusEvent().getStatusCode();
        PaperTrackingsErrors paperTrackingsErrors = PaperTrackingsErrorsMapper.buildPaperTrackingsError(context.getPaperTrackings(),
                List.of(statusCode),
                ErrorCategory.NOT_RETRYABLE_EVENT_ERROR,
                null,
                EventStatusCodeEnum.fromKey(statusCode).getStatusCodeDescription(),
                FlowThrow.NOT_RETRYABLE_EVENT_HANDLER,
                ErrorType.WARNING
        );
        return paperTrackerExceptionHandler.handleRetryError(paperTrackingsErrors);
    }
}
