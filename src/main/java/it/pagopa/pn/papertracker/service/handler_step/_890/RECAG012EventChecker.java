package it.pagopa.pn.papertracker.service.handler_step._890;

import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Attachment;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Event;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperStatus;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import it.pagopa.pn.papertracker.utils.OcrUtility;
import it.pagopa.pn.papertracker.utils.TrackerUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static it.pagopa.pn.papertracker.utils.TrackerUtility.checkIfIsFinalDemat;
import static it.pagopa.pn.papertracker.utils.TrackerUtility.findRECAG012Event;

@Component
@RequiredArgsConstructor
@Slf4j
public class RECAG012EventChecker implements HandlerStep {

    private final OcrUtility ocrUtility;

    /**
     * Step che effettua i seguenti passaggi:<br>
     * Verifica se tutti gli allegati richiesti sono presenti tra gli eventi salvati e che l'evento RECAG012 sia presente.<br>
     * Se entrambe le condizioni sono soddisfatte<br>
     * - imposta l'attributo refinementCondition a true nel contesto<br>
     * - verifica se l'OCR è abilitato:<br>
     * -- se abilitato, invia gli allegati necessari all'OCR e prosegue il flusso<br>
     * -- se non abilitato, prosegue con i successivi step<br>
     * Altrimenti, se una delle condizioni non è soddisfatta, il flusso prosegue con i successivi step senza ulteriori azioni.<br>
     *
     * @param context Contesto contenente le informazioni necessarie per l'elaborazione dell'evento.
     * @return Mono(Void)
     */
    @Override
    public Mono<Void> execute(HandlerContext context) {
        log.info("Starting RECAG012EventChecker for trackingId {}", context.getTrackingId());

        Optional<Event> recag012Event = findRECAG012Event(context.getPaperTrackings());
        List<String> requiredAttachments = context.getPaperTrackings().getValidationConfig().getRequiredAttachmentsRefinementStock890();
        List<String> ocrAttachments = context.getPaperTrackings().getValidationConfig().getSendOcrAttachmentsRefinementStock890();
        context.setNeedToSendRECAG012A(false);

        if(recag012Event.isEmpty()){
            log.info("RECAG012 event not found for trackingId {}", context.getTrackingId());
            return Mono.empty();
        }

        // Non ha tutti gli allegati richiesti al refinement ma è arrivato il RECAG012
        if (!hasRequiredAttachments(context, requiredAttachments)) {
            log.info("Missing required attachments for trackingId {}", context.getTrackingId());
            context.setNeedToSendRECAG012A(true);
            return Mono.empty();
        }

        PaperStatus paperStatus = context.getPaperTrackings().getPaperStatus();

        Map<String, List<Attachment>> attachments = context.getPaperTrackings().getEvents().stream()
                .filter(event -> !CollectionUtils.isEmpty(event.getAttachments()))
                .peek(event -> {
                    // Popola il registeredLetterCode perchè non presente prima del SequenceValidator890
                    if(checkIfIsFinalDemat(event.getStatusCode()))
                        paperStatus.setRegisteredLetterCode(event.getRegisteredLetterCode());
                })
                .map(event -> Map.entry(
                            event.getId(),
                            event.getAttachments().stream()
                                    .filter(att -> ocrAttachments.contains(att.getDocumentType()))
                                    .toList()
                    ))
                .filter(entry -> !CollectionUtils.isEmpty(entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return ocrUtility.checkAndSendToOcr(recag012Event.get(), attachments, context).then();
    }

    private boolean hasRequiredAttachments(HandlerContext context, List<String> requiredAttachments) {
        Set<String> documentTypes = context.getPaperTrackings().getEvents().stream()
                .map(Event::getAttachments)
                .filter(attachments -> !CollectionUtils.isEmpty(attachments))
                .flatMap(attachmentList -> attachmentList.stream().map(Attachment::getDocumentType))
                .collect(Collectors.toSet());
        return TrackerUtility.hasRequiredAttachmentsRefinementStock890(requiredAttachments, documentTypes);
    }



}
