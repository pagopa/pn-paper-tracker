package it.pagopa.pn.papertracker.utils;

import com.sngular.apigenerator.asyncapi.business_model.model.event.DataDTO;
import com.sngular.apigenerator.asyncapi.business_model.model.event.DetailsDTO;
import com.sngular.apigenerator.asyncapi.business_model.model.event.OcrDataPayloadDTO;
import it.pagopa.pn.api.dto.events.GenericEventHeader;
import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.middleware.msclient.SafeStorageClient;
import it.pagopa.pn.papertracker.middleware.queue.model.OcrEvent;
import it.pagopa.pn.papertracker.middleware.queue.producer.OcrMomProducer;
import it.pagopa.pn.papertracker.model.DocumentTypeEnum;
import it.pagopa.pn.papertracker.model.FileType;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.model.OcrStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static it.pagopa.pn.papertracker.utils.QueueConst.OCR_REQUEST_EVENT_TYPE;
import static it.pagopa.pn.papertracker.utils.QueueConst.PUBLISHER;

@Component
@RequiredArgsConstructor
@Slf4j
public class OcrUtility {

    private final OcrMomProducer ocrMomProducer;
    private final SafeStorageClient safeStorageClient;
    private final PnPaperTrackerConfigs cfg;
    private final PaperTrackingsDAO paperTrackingsDAO;

    public Mono<OcrStatusEnum> checkAndSendToOcr(Event event, Map<String, List<Attachment>> attachmentList, HandlerContext context) {
        PaperTrackings paperTracking = context.getPaperTrackings();
        OcrStatusEnum ocrStatusEnum = context.getPaperTrackings().getValidationConfig().getOcrEnabled();

        if (Objects.nonNull(ocrStatusEnum) && !ocrStatusEnum.equals(OcrStatusEnum.DISABLED)) {
            log.info("OCR validation enabled for trackingId={}", paperTracking.getTrackingId());
            return sendMessageToOcr(event, attachmentList, paperTracking, ocrStatusEnum)
                    .thenReturn(ocrStatusEnum);
        } else {
            log.info("OCR validation disabled for trackingId={}", paperTracking.getTrackingId());
            return paperTrackingsDAO.updateItem(paperTracking.getTrackingId(), getPaperTrackingsToUpdate(OcrStatusEnum.DISABLED, event, null))
                    .doOnNext(v -> log.debug("Updated PaperTrackings entity with OCR flag disabled for trackingId={}", paperTracking.getTrackingId()))
                    .thenReturn(ocrStatusEnum);
        }
    }

    private Mono<Void> sendMessageToOcr(Event event, Map<String, List<Attachment>> attachmentList, PaperTrackings paperTracking, OcrStatusEnum ocrStatusEnum) {
        Instant now = Instant.now();
        Map<String, List<Attachment>> validAttachmentList = attachmentList.entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                        .map(Attachment::getUri)
                        .map(this::retrieveFileType)
                        .anyMatch(ext -> cfg.getEnableOcrValidationForFile().contains(FileType.fromValue(ext))))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));


        if (validAttachmentList.isEmpty()) {
            log.info("No attachment type supported found for OCR, skipping OCR request for trackingId={}", paperTracking.getTrackingId());
            return paperTrackingsDAO.updateItem(paperTracking.getTrackingId(), getPaperTrackingsToUpdate(ocrStatusEnum, event, null))
                    .then();
        }

        List<OcrRequest> ocrRequests = new ArrayList<>();
        return Flux.fromIterable(validAttachmentList.entrySet())
                .flatMap(attachmentEntry ->  processAttachments(attachmentEntry, paperTracking, event, ocrRequests, now))
                .collectList()
                .flatMap(unused -> paperTrackingsDAO.updateItem(paperTracking.getTrackingId(), getPaperTrackingsToUpdate(ocrStatusEnum, event, ocrRequests)))
                .doOnNext(unused -> log.info("OCR validation completed for trackingId={}", paperTracking.getTrackingId()))
                .then();
    }

    private Flux<String> processAttachments(Map.Entry<String, List<Attachment>> attachmentEntry, PaperTrackings paperTracking, Event event, List<OcrRequest> ocrRequests, Instant now) {
        return Flux.fromIterable(attachmentEntry.getValue())
                .flatMap(attachment -> processAttachmentForOcr(paperTracking, attachment, attachmentEntry.getKey(), event, ocrRequests, now));
    }

    private Mono<String> processAttachmentForOcr(PaperTrackings paperTracking, Attachment attachment, String attachmentEventId, Event event, List<OcrRequest> ocrRequests, Instant now) {
        String documentType = attachment.getDocumentType();
        String ocrRequestId = TrackerUtility.buildOcrRequestId(paperTracking.getTrackingId(), event.getId(), documentType);
        ocrRequests.add(getOcrRequest(documentType, event.getId(), attachmentEventId, attachment.getUri(), now));

        return safeStorageClient.getSafeStoragePresignedUrl(attachment.getUri())
                .doOnNext(presignedUrl -> {
                    OcrEvent ocrEvent = buildOcrEvent(paperTracking, ocrRequestId, presignedUrl, DocumentTypeEnum.fromValue(documentType), event);
                    ocrMomProducer.push(ocrEvent);
                });
    }

    private OcrRequest getOcrRequest(String documentType, String finalEventId, String attachmentEventId, String uri, Instant now) {
        OcrRequest ocrRequest = new OcrRequest();
        ocrRequest.setDocumentType(documentType);
        ocrRequest.setFinalEventId(finalEventId);
        ocrRequest.setAttachmentEventId(attachmentEventId);
        ocrRequest.setRequestTimestamp(now);
        ocrRequest.setUri(uri);
        return ocrRequest;
    }

    private String retrieveFileType(String uri) {
        return FilenameUtils.getExtension(uri);
    }

    private PaperTrackings getPaperTrackingsToUpdate(OcrStatusEnum ocrStatusEnum, Event event, List<OcrRequest> ocrRequests) {
        PaperTrackings paperTracking = new PaperTrackings();
        ValidationConfig validationConfig = new ValidationConfig();
        validationConfig.setOcrEnabled(ocrStatusEnum);
        paperTracking.setValidationConfig(validationConfig);

        switch (ocrStatusEnum) {
            case DISABLED:
                TrackerUtility.setDematValidationTimestamp(paperTracking, event.getStatusCode());
                break;
            case DRY:
                TrackerUtility.setDematValidationTimestamp(paperTracking, event.getStatusCode());
                paperTracking.getValidationFlow().setOcrRequests(ocrRequests);
                break;
            case RUN:
                paperTracking.setValidationFlow(createValidationFlow(ocrRequests));
                TrackerUtility.setNewStatus(paperTracking, event.getStatusCode(), BusinessState.AWAITING_OCR, PaperTrackingsState.AWAITING_OCR);
                break;
        }

        return paperTracking;
    }

    private ValidationFlow createValidationFlow(List<OcrRequest> ocrRequests) {
        ValidationFlow validationFlow = new ValidationFlow();
        validationFlow.setOcrRequests(ocrRequests);
        return validationFlow;
    }

    private OcrEvent buildOcrEvent(PaperTrackings paperTracking, String ocrRequestId, String presignedUrl, DocumentTypeEnum documentType, Event event) {

        GenericEventHeader ocrHeader = GenericEventHeader.builder()
                .publisher(PUBLISHER)
                .eventId(UUID.randomUUID().toString())
                .createdAt(Instant.now())
                .eventType(OCR_REQUEST_EVENT_TYPE)
                .build();

        DetailsDTO.DeliveryFailureCause deliveryFailureCause = StringUtils.isNotBlank(paperTracking.getPaperStatus().getDeliveryFailureCause()) ?
                DetailsDTO.DeliveryFailureCause.valueOf(paperTracking.getPaperStatus().getDeliveryFailureCause()) : null;

        DataDTO.UnifiedDeliveryDriver unifiedDeliveryDriver = StringUtils.isNotBlank(paperTracking.getUnifiedDeliveryDriver()) ?
                DataDTO.UnifiedDeliveryDriver.valueOf(paperTracking.getUnifiedDeliveryDriver().toUpperCase()) : null;

        OcrDataPayloadDTO ocrDataPayload = OcrDataPayloadDTO.builder()
                .version("v1")
                .commandType(OcrDataPayloadDTO.CommandType.POSTAL)
                .commandId(ocrRequestId)
                .data(DataDTO.builder()
                        .documentType(DataDTO.DocumentType.valueOf(documentType.name()))
                        .productType(getProductType(paperTracking))
                        .unifiedDeliveryDriver(unifiedDeliveryDriver)
                        .details(
                                DetailsDTO.builder()
                                        .attachment(presignedUrl)
                                        .registeredLetterCode(paperTracking.getPaperStatus().getRegisteredLetterCode())
                                        .notificationDate(LocalDateTime.ofInstant(event.getStatusTimestamp(), ZoneOffset.UTC))
                                        .deliveryFailureCause(deliveryFailureCause)
                                        .deliveryDetailCode(event.getStatusCode())
                                        .build()
                        )
                        .build())
                .build();

        return new OcrEvent(ocrHeader, ocrDataPayload);
    }

    private DataDTO.ProductType getProductType(PaperTrackings paperTracking) {
        return Arrays.stream(DataDTO.ProductType.values()).filter(ocrProductType -> ocrProductType.getValue().equals(paperTracking.getProductType()))
                .findFirst()
                .orElseThrow(() -> {
                    log.error("invalid ProductType for trackingId={}: {}", paperTracking.getTrackingId(), paperTracking.getProductType());
                    return new IllegalArgumentException("Invalid product type: " + paperTracking.getProductType());
                });
    }
}
