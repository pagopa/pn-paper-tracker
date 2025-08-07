package it.pagopa.pn.papertracker.service.handler_step.AR;

import com.sngular.apigenerator.asyncapi.business_model.model.event.DataDTO;
import com.sngular.apigenerator.asyncapi.business_model.model.event.DetailsDTO;
import com.sngular.apigenerator.asyncapi.business_model.model.event.OcrDataPayloadDTO;
import it.pagopa.pn.api.dto.events.GenericEventHeader;
import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.queue.model.OcrEvent;
import it.pagopa.pn.papertracker.middleware.queue.producer.OcrMomProducer;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DematValidator implements HandlerStep {

    private static final Logger log = LoggerFactory.getLogger(DematValidator.class);

    private static final String PUBLISHER = "paper-tracker-ocr";
    private static final String OCR_REQUEST_EVENT_TYPE = "OCR_REQUEST";

    private final PaperTrackingsDAO paperTrackingsDAO;
    private final PnPaperTrackerConfigs cfg;
    private final OcrMomProducer ocrMomProducer;

    @Override
    public Mono<Void> execute(HandlerContext context) {
        return validateDemat(context.getPaperTrackings(), context.getPaperTrackings().getTrackingId(), context)
                .then();
    }

    public Mono<Void> validateDemat(PaperTrackings paperTrackings, String requestId, HandlerContext context){
        log.info("Inizio validazione Demat per requestId={}, request : {}", paperTrackings.getTrackingId(), paperTrackings);
        return Mono.just(paperTrackings)
                .flatMap(paperTracking -> {
                    //Setto questi parametri a null per non mandare in errore l'updateItem, visto che sono già considerati nel metodo
                    paperTracking.setUpdatedAt(null);
                    paperTracking.setTrackingId(null);
                    if (cfg.isEnableOcrValidation()) {
                        log.debug("OCR validation abilitata");
                        return sendMessageToOcr(paperTracking, requestId);
                    } else {
                        log.debug("OCR validation disabilitata");
                        // Imposta un flag nel context per indicare di fermare l'esecuzione
                        context.setStopExecution(true);
                        return disableOcrAndUpdate(paperTracking, requestId);
                    }
                })
                .onErrorResume(e -> Mono.error(new RuntimeException("Errore durante la validazione Demat", e)));
    }

    private Mono<Void> sendMessageToOcr(PaperTrackings paperTracking, String requestId) {
        OcrEvent ocrEvent = buildOcrEvent(paperTracking);
        paperTracking.setOcrRequestId(ocrEvent.getPayload().getCommandId());
        paperTracking.getValidationFlow().setDematValidationTimestamp(Instant.now());
        return paperTrackingsDAO.updateItem(requestId, paperTracking)
                .then(Mono.fromRunnable(() -> {
                    log.info("Push evento OCR su coda per requestId={}, ocrRequestId={}", requestId, ocrEvent.getPayload().getCommandId());
                    ocrMomProducer.push(ocrEvent);
                }));
    }

    private OcrEvent buildOcrEvent(PaperTrackings paperTracking) {
        GenericEventHeader ocrHeader = GenericEventHeader.builder()
                .publisher(PUBLISHER)
                .eventId(UUID.randomUUID().toString())
                .createdAt(Instant.now())
                .eventType(OCR_REQUEST_EVENT_TYPE)
                .build();

        OcrDataPayloadDTO ocrDataPayload = OcrDataPayloadDTO.builder()
                .version("v1")
                .commandType(OcrDataPayloadDTO.CommandType.POSTAL)
                .commandId(UUID.randomUUID().toString())
                .data(DataDTO.builder()
                        .productType(getProductType(paperTracking))
                        .unifiedDeliveryDriver(DataDTO.UnifiedDeliveryDriver.valueOf(paperTracking.getUnifiedDeliveryDriver()))
                        .details(
                                DetailsDTO.builder()
                                        .registeredLetterCode(paperTracking.getPaperStatus().getRegisteredLetterCode())
                                        .notificationDate(LocalDateTime.ofInstant(paperTracking.getValidationFlow().getSequencesValidationTimestamp(), ZoneId.systemDefault()))
                                        .build()
                        )
                        .build())
                .build();

        return new OcrEvent(ocrHeader, ocrDataPayload);
    }

    private Mono<Void> disableOcrAndUpdate(PaperTrackings paperTracking, String requestId) {
        paperTracking.getValidationFlow().setOcrEnabled(false);
        paperTracking.getValidationFlow().setDematValidationTimestamp(Instant.now());
        return paperTrackingsDAO.updateItem(requestId, paperTracking)
                .doOnSuccess(v -> log.debug("Aggiornato PaperTrackings con OCR disabilitato per requestId={}", paperTracking.getTrackingId()))
                .then();
    }

    private DataDTO.ProductType getProductType(PaperTrackings paperTracking) {
        return Arrays.stream(DataDTO.ProductType.values()).filter(ocrProductType -> ocrProductType.getValue().equals(paperTracking.getProductType().getValue()))
                .findFirst()
                .orElseThrow(() -> {
                    log.error("ProductType non valido per requestId={}: {}", paperTracking.getTrackingId(), paperTracking.getProductType());
                    return new IllegalArgumentException("Invalid product type: " + paperTracking.getProductType());
                });
    }
}