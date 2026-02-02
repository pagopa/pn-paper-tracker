package it.pagopa.pn.papertracker.service.handler_step.generic;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.exception.PaperTrackerException;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.middleware.msclient.SafeStorageClient;
import it.pagopa.pn.papertracker.middleware.queue.model.OcrEvent;
import it.pagopa.pn.papertracker.middleware.queue.producer.OcrMomProducer;
import it.pagopa.pn.papertracker.model.FileType;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.model.OcrStatusEnum;
import it.pagopa.pn.papertracker.utils.OcrUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DematValidatorTest {

    @Mock
    PaperTrackingsDAO paperTrackingsDAO;
    @Mock
    PnPaperTrackerConfigs cfg;
    @Mock
    OcrMomProducer ocrMomProducer;
    @Mock
    SafeStorageClient safeStorageClient;

    DematValidator dematValidator;

    PaperTrackings paperTrackings;

    HandlerContext context;

    @BeforeEach
    void setUp() {
        context = new HandlerContext();
        OcrUtility ocrUtility = new OcrUtility(ocrMomProducer, safeStorageClient, cfg, paperTrackingsDAO);
        paperTrackings = new PaperTrackings();
        paperTrackings.setTrackingId("req-123");
        paperTrackings.setProductType(ProductType.AR.getValue());
        paperTrackings.setUnifiedDeliveryDriver("POSTE");
        paperTrackings.setPaperStatus(new PaperStatus());
        paperTrackings.getPaperStatus().setRegisteredLetterCode("RL123");
        ValidationFlow validationFlow = new ValidationFlow();
        validationFlow.setSequencesValidationTimestamp(Instant.now());
        paperTrackings.setValidationFlow(validationFlow);
        context.setPaperTrackings(paperTrackings);
        context.setEventId("eventId1");
        ValidationConfig validationConfig = new ValidationConfig();
        validationConfig.setStrictFinalValidationStock890(Boolean.TRUE);
        validationConfig.setSendOcrAttachmentsFinalValidationStock890(List.of("ARCAD", "CAD"));
        validationConfig.setSendOcrAttachmentsFinalValidation(List.of("Plico", "AR", "23L"));
        validationConfig.setRequiredAttachmentsRefinementStock890(List.of("23L"));
        validationConfig.setOcrEnabled(OcrStatusEnum.DISABLED);
        paperTrackings.setValidationConfig(validationConfig);
        dematValidator = new DematValidator(ocrUtility) { };
    }

    private Event getEvent(String statusCode, String documentType, String eventId) {
        Event event = new Event();
        event.setStatusCode(statusCode);
        event.setProductType(ProductType.AR.getValue());
        event.setId(eventId);
        event.setStatusTimestamp(Instant.now());
        if (StringUtils.hasText(documentType)) {
            Attachment attachment = new Attachment();
            attachment.setUri("uri.pdf");
            attachment.setDocumentType("Indagine");
            Attachment attachment1 = new Attachment();
            attachment1.setUri("uri.pdf");
            attachment1.setDocumentType(documentType);
            event.setAttachments(List.of(attachment, attachment1));
        }
        return event;

    }

    @Test
    void validateDemat_OcrEnabled_UpdatesItemAndPushesEvent() {
        // Arrange
        context.getPaperTrackings().setEvents(List.of(getEvent("RECRN005C", null, "eventId1"), getEvent("RECRN005A", null, "eventId2"), getEvent("RECRN005B", "Plico", "eventId3")));
        context.getPaperTrackings().getValidationConfig().setOcrEnabled(OcrStatusEnum.RUN);
        context.getPaperTrackings().getPaperStatus().setValidatedEvents(List.of("eventId1", "eventId2", "eventId3"));

        when(cfg.getEnableOcrValidationForFile()).thenReturn(List.of(FileType.PDF));
        when(safeStorageClient.getSafeStoragePresignedUrl(any())).thenReturn(Mono.just("presigned-url"));
        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.just(context.getPaperTrackings()));

        // Act
        StepVerifier.create(dematValidator.validateDemat(context))
                .verifyComplete();

        // Assert
        verify(safeStorageClient, times(1)).getSafeStoragePresignedUrl(any());
        verify(paperTrackingsDAO, times(1)).updateItem(any(), any());
        verify(ocrMomProducer, times(1)).push(any(OcrEvent.class));
    }

    @Test
    void validateDemat_OcrEnabled_UpdatesItemAndPushesEvent2() {
        // Arrange
        context.getPaperTrackings().setEvents(List.of(getEvent("RECRN002F", null, "eventId1"), getEvent("RECRN002D", null, "eventId2"), getEvent("RECRN002E", "AR", "eventId3")));
        context.getPaperTrackings().getPaperStatus().setValidatedEvents(List.of("eventId1", "eventId2", "eventId3"));
        context.getPaperTrackings().getValidationConfig().setOcrEnabled(OcrStatusEnum.RUN);

        when(cfg.getEnableOcrValidationForFile()).thenReturn(List.of(FileType.PDF));
        when(safeStorageClient.getSafeStoragePresignedUrl(any())).thenReturn(Mono.just("presigned-url"));
        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.just(context.getPaperTrackings()));

        // Act
        StepVerifier.create(dematValidator.validateDemat(context))
                .verifyComplete();

        // Assert
        verify(safeStorageClient, times(1)).getSafeStoragePresignedUrl(any());
        verify(paperTrackingsDAO, times(1)).updateItem(any(), any());
        verify(ocrMomProducer, times(1)).push(any(OcrEvent.class));
    }

    @Test
    void validateDemat_OcrDisabled_UpdatesItemAndDoesNotPushEvent() {
        // Arrange
        context.getPaperTrackings().getValidationConfig().setOcrEnabled(OcrStatusEnum.DISABLED);
        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.empty());
        context.getPaperTrackings().setEvents(List.of(getEvent("RECRN005C", null, "eventId1"), getEvent("RECRN005A", null, "eventId2"), getEvent("RECRN005B", "Plico", "eventId3")));
        context.getPaperTrackings().getPaperStatus().setValidatedEvents(List.of("eventId1", "eventId2", "eventId3"));

        // Act
        StepVerifier.create(dematValidator.validateDemat(context))
                .verifyComplete();

        // Assert
        verifyNoInteractions(safeStorageClient);
        verify(paperTrackingsDAO, times(1)).updateItem(any(), any());
        verify(ocrMomProducer, never()).push(any(OcrEvent.class));
    }

    @Test
    void validateDemat_UpdateItemThrowsError_PropagatesError() {
        // Arrange
        context.getPaperTrackings().getValidationConfig().setOcrEnabled(OcrStatusEnum.RUN);
        when(cfg.getEnableOcrValidationForFile()).thenReturn(List.of(FileType.PDF));

        when(safeStorageClient.getSafeStoragePresignedUrl(any())).thenReturn(Mono.just("presigned-url"));
        context.getPaperTrackings().setEvents(List.of(getEvent("RECRN005C", null, "eventId1"), getEvent("RECRN005A", null, "eventId2"), getEvent("RECRN005B", "Plico", "eventId3")));
        context.getPaperTrackings().getPaperStatus().setValidatedEvents(List.of("eventId1", "eventId2", "eventId3"));

        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.error(new RuntimeException("DB error")));

        // Act
        StepVerifier.create(dematValidator.validateDemat(context))
                .expectErrorMatches(e -> e instanceof PaperTrackerException && e.getMessage().contains("Error during Demat Validation"))
                .verify();

        // Assert
        verify(ocrMomProducer, times(1)).push(any(OcrEvent.class));
        verify(safeStorageClient, times(1)).getSafeStoragePresignedUrl(any());
        verify(paperTrackingsDAO, times(1)).updateItem(any(), any());
    }

}
