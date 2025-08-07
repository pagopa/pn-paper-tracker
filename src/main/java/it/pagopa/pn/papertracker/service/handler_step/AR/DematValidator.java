package it.pagopa.pn.papertracker.service.handler_step.AR;

import com.sngular.apigenerator.asyncapi.business_model.model.event.DataDTO;
import com.sngular.apigenerator.asyncapi.business_model.model.event.DetailsDTO;
import com.sngular.apigenerator.asyncapi.business_model.model.event.OcrDataPayloadDTO;
import it.pagopa.pn.api.dto.events.GenericEventHeader;
import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.exception.PaperTrackerException;
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
        return validateDemat(context)
                .then();
    }

    public Mono<Void> validateDemat(HandlerContext context){
        PaperTrackings paperTrackings = context.getPaperTrackings();
        String trackingId = paperTrackings.getTrackingId();
        log.info("Start demait validation for trackingId={}", trackingId);
        return Mono.just(paperTrackings)
                .flatMap(paperTracking -> {
                    if (cfg.isEnableOcrValidation()) {
                        log.debug("OCR validation enabled");
                        context.setStopExecution(true);
                        return sendMessageToOcr(paperTracking, context.getEventId());
                    } else {
                        log.debug("OCR validation disabled");
                        return updatePaperTrackingsOcrFlag(paperTracking, trackingId);
                    }
                })
                .onErrorResume(e -> Mono.error(new PaperTrackerException("Error during Demat Validation", e)));
    }

    private Mono<Void> sendMessageToOcr(PaperTrackings paperTracking, String eventId) {
        String ocrRequestId = String.join("#", paperTracking.getTrackingId(), eventId);
        OcrEvent ocrEvent = buildOcrEvent(paperTracking, ocrRequestId);
        paperTracking.setOcrRequestId(ocrEvent.getPayload().getCommandId());
        paperTracking.getValidationFlow().setDematValidationTimestamp(Instant.now());
        return paperTrackingsDAO.updateItem(paperTracking.getTrackingId(), paperTracking)
                .doOnNext(paperTrackings -> {
                    log.info("Send message to OCR queue for trackingId={}, with commandId={}", paperTrackings.getTrackingId(), ocrEvent.getPayload().getCommandId());
                    ocrMomProducer.push(ocrEvent);
                })
                .then();
    }

    private OcrEvent buildOcrEvent(PaperTrackings paperTracking, String ocrRequestId) {
        GenericEventHeader ocrHeader = GenericEventHeader.builder()
                .publisher(PUBLISHER)
                .eventId(UUID.randomUUID().toString())
                .createdAt(Instant.now())
                .eventType(OCR_REQUEST_EVENT_TYPE)
                .build();

        OcrDataPayloadDTO ocrDataPayload = OcrDataPayloadDTO.builder()
                .version("v1")
                .commandType(OcrDataPayloadDTO.CommandType.POSTAL)
                .commandId(ocrRequestId)
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

    private Mono<Void> updatePaperTrackingsOcrFlag(PaperTrackings paperTracking, String requestId) {
        paperTracking.getValidationFlow().setOcrEnabled(false);
        paperTracking.getValidationFlow().setDematValidationTimestamp(Instant.now());
        return paperTrackingsDAO.updateItem(requestId, paperTracking)
                .doOnNext(v -> log.debug("Updated PaperTrackings entity with OCR flag disabled for trackingId={}", paperTracking.getTrackingId()))
                .then();
    }

    private DataDTO.ProductType getProductType(PaperTrackings paperTracking) {
        return Arrays.stream(DataDTO.ProductType.values()).filter(ocrProductType -> ocrProductType.getValue().equals(paperTracking.getProductType().getValue()))
                .findFirst()
                .orElseThrow(() -> {
                    log.error("invalid ProductType for trackingId={}: {}", paperTracking.getTrackingId(), paperTracking.getProductType());
                    return new IllegalArgumentException("Invalid product type: " + paperTracking.getProductType());
                });
    }
}