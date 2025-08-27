package it.pagopa.pn.papertracker.service.handler_step;

import com.sngular.apigenerator.asyncapi.business_model.model.event.DataDTO;
import com.sngular.apigenerator.asyncapi.business_model.model.event.DetailsDTO;
import com.sngular.apigenerator.asyncapi.business_model.model.event.OcrDataPayloadDTO;
import it.pagopa.pn.api.dto.events.GenericEventHeader;
import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.config.StatusCodeConfiguration;
import it.pagopa.pn.papertracker.exception.PaperTrackerException;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.middleware.msclient.SafeStorageClient;
import it.pagopa.pn.papertracker.middleware.queue.model.OcrEvent;
import it.pagopa.pn.papertracker.middleware.queue.producer.OcrMomProducer;
import it.pagopa.pn.papertracker.model.DocumentTypeEnum;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.model.OcrDocumentTypeEnum;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import org.apache.commons.io.FilenameUtils;

@Service
@RequiredArgsConstructor
public class DematValidator implements HandlerStep {

    private static final Logger log = LoggerFactory.getLogger(DematValidator.class);

    private static final String PUBLISHER = "paper-tracker-ocr";
    private static final String OCR_REQUEST_EVENT_TYPE = "OCR_REQUEST";

    private final PaperTrackingsDAO paperTrackingsDAO;
    private final PnPaperTrackerConfigs cfg;
    private final OcrMomProducer ocrMomProducer;
    private final SafeStorageClient safeStorageClient;

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
                .flatMap(paperTracking -> {
                    if (cfg.getEnableOcrValidationFor().contains(paperTracking.getProductType().name())) {
                        log.debug("OCR validation enabled");
                        return sendMessageToOcr(paperTracking, context);
                    } else {
                        log.debug("OCR validation disabled");
                        return paperTrackingsDAO.updateItem(trackingId, getPaperTrackingsToUpdate(false, null, null))
                                .doOnNext(v -> log.debug("Updated PaperTrackings entity with OCR flag disabled for trackingId={}", paperTracking.getTrackingId()))
                                .then();
                    }
                })
                .onErrorResume(e -> Mono.error(new PaperTrackerException("Error during Demat Validation", e)));
    }

    private Mono<Void> sendMessageToOcr(PaperTrackings paperTracking, HandlerContext context) {
        String ocrRequestId = String.join("#", paperTracking.getTrackingId(), context.getEventId());
        Attachment attachment = retrieveFinalDemat(paperTracking.getTrackingId(), paperTracking.getPaperStatus().getValidatedEvents());
        if(cfg.getEnableOcrValidationForFile().contains(retrieveFileType(attachment.getUri()))) {
            context.setStopExecution(true);
            return safeStorageClient.getSafeStoragePresignedUrl(attachment.getUri())
                    .doOnNext(presignedUrl -> {
                        OcrEvent ocrEvent = buildOcrEvent(paperTracking, ocrRequestId, presignedUrl, DocumentTypeEnum.fromValue(attachment.getDocumentType()));
                        ocrMomProducer.push(ocrEvent);
                    })
                    .map(ocrEvent -> getPaperTrackingsToUpdate(true, ocrRequestId, PaperTrackingsState.AWAITING_OCR))
                    .flatMap(paperTrackingsToUpdate -> paperTrackingsDAO.updateItem(paperTracking.getTrackingId(), paperTrackingsToUpdate))
                    .doOnNext(unused -> log.info("Demat validation completed for trackingId={}", paperTracking.getTrackingId()))
                    .then();
        }
        log.info("Attachment type {} not supported for OCR, skipping OCR request for trackingId={}", attachment.getDocumentType(), paperTracking.getTrackingId());
        return Mono.empty();
    }

    private String retrieveFileType(String uri) {
        return FilenameUtils.getExtension(uri);
    }

    private PaperTrackings getPaperTrackingsToUpdate(boolean ocrEnabled, String ocrRequestId, PaperTrackingsState state){
        PaperTrackings paperTracking = new PaperTrackings();
        ValidationFlow validationFlow = new ValidationFlow();
        if(ocrEnabled){
            validationFlow.setOcrEnabled(true);
            validationFlow.setOcrRequestTimestamp(Instant.now());
            paperTracking.setOcrRequestId(ocrRequestId);
            paperTracking.setState(state);
        }else{
            validationFlow.setOcrEnabled(false);
            validationFlow.setDematValidationTimestamp(Instant.now());
        }
        paperTracking.setValidationFlow(validationFlow);
        return paperTracking;
    }

    private OcrEvent buildOcrEvent(PaperTrackings paperTracking, String ocrRequestId, String presignedUrl, DocumentTypeEnum documentType) {

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
                        .documentType(DataDTO.DocumentType.valueOf(documentType.name()))
                        .productType(getProductType(paperTracking))
                        .unifiedDeliveryDriver(DataDTO.UnifiedDeliveryDriver.valueOf(paperTracking.getUnifiedDeliveryDriver()))
                        .details(
                                DetailsDTO.builder()
                                        .attachment(presignedUrl)
                                        .registeredLetterCode(paperTracking.getPaperStatus().getRegisteredLetterCode())
                                        .notificationDate(LocalDateTime.ofInstant(paperTracking.getValidationFlow().getSequencesValidationTimestamp(), ZoneId.systemDefault()))
                                        .build()
                        )
                        .build())
                .build();

        return new OcrEvent(ocrHeader, ocrDataPayload);
    }

    private Attachment retrieveFinalDemat(String trackingId, List<Event> validatedEvents) {
        Map<String, Attachment> attachments = new HashMap<>();

        List<Event> finalDematList = validatedEvents.reversed().stream()
                .filter(event -> StatusCodeConfiguration.StatusCodeConfigurationEnum.fromKey(event.getStatusCode()).isFinalDemat())
                .toList();

        if (CollectionUtils.isEmpty(finalDematList)) {
            log.error("Demat events not found for trackingId={}", trackingId);
            throw new PaperTrackerException("Demat events not found");
        }

        for (Event event : finalDematList) {
            Optional.ofNullable(event.getAttachments()).orElse(new ArrayList<>())
                    .stream()
                    .filter(att -> OcrDocumentTypeEnum.valueOf(event.getProductType().name()).getDocumentTypes().contains(DocumentTypeEnum.fromValue(att.getDocumentType())))
                    .forEach(attachment -> attachments.putIfAbsent(attachment.getDocumentType(), attachment));
        }

        if (CollectionUtils.isEmpty(attachments) || attachments.size() > 1) {
            log.error("Invalid number of attachments for demat event found for trackingId={}", trackingId);
            throw new PaperTrackerException("Invalid number of attachments for demat event");
        }

        return attachments.values().stream().toList().getFirst();
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