package it.pagopa.pn.papertracker.service.handler_step._890;

import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Attachment;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Event;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import it.pagopa.pn.papertracker.utils.OcrUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static it.pagopa.pn.papertracker.model.EventStatusCodeEnum.RECAG012;

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

        Optional<Event> recag012Event = findRECAG012Event(context);
        List<String> requiredAttachments = context.getPaperTrackings().getValidationConfig().getRequiredAttachmentsRefinementStock890();

        if (!hasRequiredAttachments(context, requiredAttachments) || recag012Event.isEmpty()) {
            log.info("Missing required attachments or RECAG012 event not found for trackingId {}", context.getTrackingId());
            return Mono.empty();
        }

        context.setRefinementCondition(true);
        return ocrUtility.checkAndSendToOcr(recag012Event.get(), requiredAttachments, context);
    }

    private boolean hasRequiredAttachments(HandlerContext context, List<String> requiredAttachments) {
        Set<String> documentTypes = context.getPaperTrackings().getEvents().stream()
                .flatMap(event -> event.getAttachments().stream())
                .map(Attachment::getDocumentType)
                .collect(Collectors.toSet());
        return documentTypes.containsAll(requiredAttachments);
    }

    private Optional<Event> findRECAG012Event(HandlerContext context) {
        return context.getPaperTrackings().getEvents().stream()
                .filter(event -> RECAG012.name().equalsIgnoreCase(event.getStatusCode()))
                .findFirst();
    }

}
