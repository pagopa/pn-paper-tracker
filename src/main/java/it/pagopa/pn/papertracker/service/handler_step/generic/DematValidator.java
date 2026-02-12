package it.pagopa.pn.papertracker.service.handler_step.generic;

import it.pagopa.pn.papertracker.exception.PaperTrackerException;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Attachment;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Event;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.model.OcrStatusEnum;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import it.pagopa.pn.papertracker.utils.OcrUtility;
import it.pagopa.pn.papertracker.utils.TrackerUtility;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DematValidator implements HandlerStep {

    private static final Logger log = LoggerFactory.getLogger(DematValidator.class);

    private final OcrUtility ocrUtility;

    /**
     * Step che gestisce la validazione dematerializzazione. Se la validazione OCR è abilitata, invia un messaggio al servizio OCR e aggiorna lo stato del tracciamento.
     * Se la validazione OCR non è abilitata, aggiorna direttamente lo stato del tracciamento.
     *
     * @param context Contesto contenente le informazioni necessarie per l'elaborazione dell'evento.
     * @return Mono(Void)
     */
    @Override
    public Mono<Void> execute(HandlerContext context) {
        return validateDemat(context)
                .then();
    }

    public Mono<Void> validateDemat(HandlerContext context) {
        PaperTrackings paperTrackings = context.getPaperTrackings();
        String trackingId = paperTrackings.getTrackingId();
        log.info("Starting demat validation for trackingId={}", trackingId);

        Event currentEvent = TrackerUtility.extractEventFromContext(context);
        List<String> requiredAttachments = getRequiredAttachments(currentEvent, paperTrackings);
        List<Event> validatedEvent = TrackerUtility.validatedEvents(paperTrackings.getPaperStatus().getValidatedEvents(), paperTrackings.getEvents());
        Map<String, List<Attachment>> attachmentList = retrieveFinalDemat(validatedEvent, requiredAttachments);
        return ocrUtility.checkAndSendToOcr(currentEvent, attachmentList, context)
                .onErrorResume(e -> Mono.error(new PaperTrackerException("Error during Demat Validation", e)))
                .filter(isSentToOcr -> Boolean.TRUE.equals(isSentToOcr))
                // Ferma l'esecuzione degli step se è stato inviato all'OCR
                .doOnNext(unused -> context.setStopExecution(true))
                .then();
    }

    private Map<String, List<Attachment>> retrieveFinalDemat(List<Event> validatedEvents, List<String> requiredAttachments) {
        return validatedEvents.stream()
                .filter(event -> !CollectionUtils.isEmpty(event.getAttachments()))
                .map(event -> Map.entry(
                        event.getId(),
                        event.getAttachments().stream()
                                .filter(att -> requiredAttachments.contains(att.getDocumentType()))
                                .toList()
                ))
                .filter(entry -> !CollectionUtils.isEmpty(entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private List<String> getRequiredAttachments(Event event, PaperTrackings paperTrackings) {
        if (TrackerUtility.isStockStatus890(event.getStatusCode()))
            return paperTrackings.getValidationConfig().getSendOcrAttachmentsFinalValidationStock890();

        return paperTrackings.getValidationConfig().getSendOcrAttachmentsFinalValidation();
    }

}