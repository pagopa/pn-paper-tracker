package it.pagopa.pn.papertracker.service.handler_step.generic;

import it.pagopa.pn.papertracker.exception.PaperTrackerException;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import it.pagopa.pn.papertracker.utils.OcrUtility;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class DematValidator implements HandlerStep {

    private static final Logger log = LoggerFactory.getLogger(DematValidator.class);

    private final OcrUtility ocrUtility;

    /**
     * Step che gestisce la validazione dematerializzazione. Se la validazione OCR è abilitata, invia un messaggio al servizio OCR e aggiorna lo stato del tracciamento.
     * Se la validazione OCR non è abilitata, aggiorna direttamente lo stato del tracciamento.
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
        return Mono.just(paperTrackings)
                .flatMap(paperTracking -> ocrUtility.checkAndSendToOcr(paperTracking, context))
                .onErrorResume(e -> Mono.error(new PaperTrackerException("Error during Demat Validation", e)));
    }

}