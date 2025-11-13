package it.pagopa.pn.papertracker.service.handler_step.generic;

import com.sngular.apigenerator.asyncapi.business_model.model.event.Data;
import com.sngular.apigenerator.asyncapi.business_model.model.event.OcrDataResultPayload;
import it.pagopa.pn.papertracker.exception.PaperTrackerException;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.mapper.PaperTrackingsErrorsMapper;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import it.pagopa.pn.papertracker.utils.TrackerUtility;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@CustomLog
public class CheckOcrResponse implements HandlerStep {

    /**
     * Step che effettua un controllo sul messaggio ricevuto dal servizio OCR.<br>
     * Se lo stato della notifica è AWAITING_OCR, il flusso prosegue, altrimenti, viene
     * generato un errore.
     * Viene aggiornato il contesto con l'eventId e dryRun recuperati dall'evento finale.<br>
     * Se lo tato di validazione dell'OCR è <br>
     * - OK: il flusso prosegue con gli step successivi <br>
     * - KO: viene generato un errore ed il flusso viene interrotto <br>
     * - PENDING: il flusso viene interrotto <br>
     *
     * @param context Contesto contenente le informazioni necessarie per l'elaborazione dell'evento.
     * @return Mono(Void)
     */
    @Override
    public Mono<Void> execute(HandlerContext context) {
        log.info("Executing CheckOcrResponse step for trackingId: {}", context.getTrackingId());

        OcrDataResultPayload ocrResultMessage = context.getOcrDataResultPayload();

        return Mono.just(context.getPaperTrackings())
                .flatMap(paperTrackings -> {
                    Event event = extractFinalEventFromOcr(paperTrackings);

                    if (isNotAwaitingOcr(paperTrackings)) {
                        return handleFinalStateError(event, paperTrackings, ocrResultMessage);
                    }

                    updateContext(context, event);

                    Data.ValidationStatus validationStatus = ocrResultMessage.getData().getValidationStatus();

                    return handleValidationStatus(validationStatus, paperTrackings, event, ocrResultMessage, context);
                })
                .then();
    }

    private void updateContext(HandlerContext context, Event event) {
        context.setEventId(event.getId());
        context.setDryRunEnabled(event.getDryRun());
    }

    private boolean isNotAwaitingOcr(PaperTrackings paperTrackings) {
        return !paperTrackings.getState().equals(PaperTrackingsState.AWAITING_OCR);
    }

    private Mono<Void> handleFinalStateError(Event event, PaperTrackings paperTrackings,
                                             OcrDataResultPayload ocrResultMessage) {
        return Mono.error(new PnPaperTrackerValidationException(
                "Error in OCR validation for requestId: " + ocrResultMessage.getCommandId(),
                PaperTrackingsErrorsMapper.buildPaperTrackingsError(
                        paperTrackings, event.getStatusCode(), ErrorCategory.OCR_VALIDATION,
                        ErrorCause.OCR_DUPLICATED_EVENT,
                        "CommandId: " + ocrResultMessage.getCommandId(),
                        FlowThrow.DEMAT_VALIDATION, ErrorType.WARNING, event.getId()
                )
        ));
    }

    private Mono<Void> handleValidationStatus(Data.ValidationStatus validationStatus, PaperTrackings paperTrackings,
                                              Event event, OcrDataResultPayload ocrResultMessage, HandlerContext context) {
        return switch (validationStatus) {
            case KO:
                yield Mono.error(new PnPaperTrackerValidationException(
                        "Error in OCR validation for requestId: " + ocrResultMessage.getCommandId(),
                        PaperTrackingsErrorsMapper.buildPaperTrackingsError(
                                paperTrackings, event.getStatusCode(), ErrorCategory.OCR_VALIDATION,
                                ErrorCause.OCR_KO, ocrResultMessage.getData().getDescription(),
                                FlowThrow.DEMAT_VALIDATION, ErrorType.ERROR, event.getId()
                        )
                ));
            case OK:
                log.info("Ocr validation successful for requestId: {}", ocrResultMessage.getCommandId());
                yield Mono.empty();
            case PENDING:
                log.info("Ocr validation is still pending for requestId: {}", ocrResultMessage.getCommandId());
                context.setStopExecution(true);
                yield Mono.empty();
        };
    }

    private Event extractFinalEventFromOcr(PaperTrackings paperTrackings) {
        //TODO: RIFATTORIZZARE DOPO IL REFACTOR DELL'OCR
        /*String eventId = TrackerUtility.getEventIdFromOcrRequestId(paperTrackings.getOcrRequestId());
        return paperTrackings.getEvents().stream()
                .filter(event -> eventId.equalsIgnoreCase(event.getId()))
                .findFirst()
                .orElseThrow(() -> new PaperTrackerException("Invalid ocr requestId: " + paperTrackings.getOcrRequestId() +
                        ". The event with id " + eventId + " does not exist in the paperTrackings events list."));*/
        return null;
    }

}
