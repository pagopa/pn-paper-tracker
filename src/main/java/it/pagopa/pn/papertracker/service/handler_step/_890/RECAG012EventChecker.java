package it.pagopa.pn.papertracker.service.handler_step._890;

import it.pagopa.pn.papertracker.config.TrackerConfigUtils;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Attachment;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import it.pagopa.pn.papertracker.utils.OcrUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import static it.pagopa.pn.papertracker.model.EventStatusCodeEnum.RECAG012;

@Component
@RequiredArgsConstructor
@Slf4j
public class RECAG012EventChecker implements HandlerStep {

    private final TrackerConfigUtils trackerConfigUtils;
    private final OcrUtility ocrUtility;

    /**
     * Step che effettua i seguenti passaggi:<br>
     * Verifica se tutti gli allegati richiesti sono presenti tra gli eventi salvati e che l'evento RECAG012 sia presente.<br>
     * Se entrambe le condizioni sono soddisfatte<br>
     * - imposta l'attributo refinementCondition a true nel contesto<br>
     * - verifica se l'OCR è abilitato:<br>
     * -- se abilitato, invia gli allegati necessari all'OCR e termina il flusso<br>
     * -- se non abilitato, prosegue con i successivi step<br>
     * Altrimenti, se una delle condizioni non è soddisfatta, il flusso prosegue con i successivi step senza ulteriori azioni.<br>
     *
     * @param context Contesto contenente le informazioni necessarie per l'elaborazione dell'evento.
     * @return Mono(Void)
     */
    @Override
    public Mono<Void> execute(HandlerContext context) {
        log.info("Starting RECAG012EventChecker for trackingId {}", context.getTrackingId());

        if (!hasRequiredAttachments(context) || !hasRECAG012Event(context)) {
            log.info("Missing required attachments or RECAG012 event not found for trackingId {}", context.getTrackingId());
            return Mono.empty();
        }

        context.setRefinementCondition(true);
        return ocrUtility.checkAndSendToOcr(context.getPaperTrackings(), context);
    }

    private boolean hasRequiredAttachments(HandlerContext context) {
        Set<String> documentTypes = context.getPaperTrackings().getEvents().stream()
                .flatMap(event -> event.getAttachments().stream())
                .map(Attachment::getDocumentType)
                .collect(Collectors.toSet());
        return documentTypes.containsAll(trackerConfigUtils.getActualRequiredAttachmentsRefinementStock890(LocalDate.now()));
    }

    private boolean hasRECAG012Event(HandlerContext context) {
        return context.getPaperTrackings().getEvents().stream()
                .anyMatch(event -> RECAG012.name().equalsIgnoreCase(event.getStatusCode()));
    }

}
