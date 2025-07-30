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
public class DematValidator {

    private static final Logger log = LoggerFactory.getLogger(DematValidator.class);

    private static final String PUBLISHER = "paper-tracker-ocr";
    private static final String OCR_REQUEST_EVENT_TYPE = "OCR_REQUEST";

    private final PaperTrackingsDAO paperTrackingsDAO;
    private final PnPaperTrackerConfigs cfg;
    private final OcrMomProducer ocrMomProducer;

    public Mono<Void> validateDemat(PaperTrackings paperTrackings) {
        log.info("Inizio validazione Demat per requestId={}, request : {}", paperTrackings.getRequestId(), paperTrackings);
        return Mono.just(paperTrackings)
                .flatMap(paperTracking -> {
                    if (cfg.isEnableOcrValidation()) {
                        log.debug("OCR validation abilitata");
                        return sendMessageToOcr(paperTracking);
                    } else {
                        log.debug("OCR validation disabilitata");
                        return disableOcrAndUpdate(paperTracking);
                    }
                })
                .onErrorResume(e -> Mono.error(new RuntimeException("Errore durante la validazione Demat", e)));
    }

    private Mono<Void> sendMessageToOcr(PaperTrackings paperTracking) {
        OcrEvent ocrEvent = buildOcrEvent(paperTracking);
        paperTracking.setOcrRequestId(ocrEvent.getPayload().getCommandId());
        return paperTrackingsDAO.updateItem(paperTracking.getRequestId(), paperTracking)
                .then(Mono.fromRunnable(() -> {
                    log.info("Push evento OCR su coda per requestId={}, ocrRequestId={}", paperTracking.getRequestId(), ocrEvent.getPayload().getCommandId());
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
                                        .registeredLetterCode(paperTracking.getNotificationState().getRegisteredLetterCode())
                                        .notificationDate(LocalDateTime.ofInstant(paperTracking.getValidationFlow().getSequencesValidationTimestamp(), ZoneId.systemDefault()))
                                        .build()
                        )
                        .build())
                .build();

        return new OcrEvent(ocrHeader, ocrDataPayload);
    }

    private Mono<Void> disableOcrAndUpdate(PaperTrackings paperTracking) {
        paperTracking.getValidationFlow().setOcrEnabled(false);
        return paperTrackingsDAO.updateItem(paperTracking.getRequestId(), paperTracking)
                .doOnSuccess(v -> log.debug("Aggiornato PaperTrackings con OCR disabilitato per requestId={}", paperTracking.getRequestId()))
                .then();
    }

    private DataDTO.ProductType getProductType(PaperTrackings paperTracking) {
        return Arrays.stream(DataDTO.ProductType.values()).filter(ocrProductType -> ocrProductType.getValue().equals(paperTracking.getProductType().getValue()))
                .findFirst()
                .orElseThrow(() -> {
                    log.error("ProductType non valido per requestId={}: {}", paperTracking.getRequestId(), paperTracking.getProductType());
                    return new IllegalArgumentException("Invalid product type: " + paperTracking.getProductType());
                });
    }
}