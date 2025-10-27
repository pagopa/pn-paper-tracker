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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @InjectMocks
    DematValidator dematValidator;

    PaperTrackings paperTrackings;

    HandlerContext context;

    @BeforeEach
    void setUp() {
        context = new HandlerContext();
        paperTrackings = new PaperTrackings();
        paperTrackings.setTrackingId("req-123");
        paperTrackings.setProductType(ProductType.AR);
        paperTrackings.setUnifiedDeliveryDriver("POSTE");
        paperTrackings.setPaperStatus(new PaperStatus());
        paperTrackings.getPaperStatus().setRegisteredLetterCode("RL123");
        ValidationFlow validationFlow = new ValidationFlow();
        validationFlow.setSequencesValidationTimestamp(Instant.now());
        paperTrackings.setValidationFlow(validationFlow);
        context.setPaperTrackings(paperTrackings);
        dematValidator = new DematValidator(paperTrackingsDAO, cfg, ocrMomProducer, safeStorageClient);
    }

    private Event getEvent(String statusCode, String documentType) {
        Event event = new Event();
        event.setStatusCode(statusCode);
        event.setProductType(ProductType.AR);
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
        PaperStatus paperStatus = new PaperStatus();
        paperStatus.setValidatedEvents(List.of(getEvent("RECRN005C", null), getEvent("RECRN005A", null), getEvent("RECRN005B", "Plico")));
        context.getPaperTrackings().setPaperStatus(paperStatus);

        when(cfg.getEnableOcrValidationFor()).thenReturn(List.of(ProductType.AR));
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
        PaperStatus paperStatus = new PaperStatus();
        paperStatus.setValidatedEvents(List.of(getEvent("RECRN002F", null), getEvent("RECRN002D", null), getEvent("RECRN002E", "AR")));
        context.getPaperTrackings().setPaperStatus(paperStatus);

        when(cfg.getEnableOcrValidationFor()).thenReturn(List.of(ProductType.AR));
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
    void validateDemat_OcrEnabled_AttachmentTypeNotSupported() {
        // Arrange
        PaperStatus paperStatus = new PaperStatus();
        Event eventRECRN005B = getEvent("RECRN005B", "Plico");
        eventRECRN005B.getAttachments().getLast().setUri("uri.zip");
        paperStatus.setValidatedEvents(List.of(getEvent("RECRN005C", null), getEvent("RECRN005A", null), eventRECRN005B));
        context.getPaperTrackings().setPaperStatus(paperStatus);

        when(cfg.getEnableOcrValidationFor()).thenReturn(List.of(ProductType.AR));
        when(cfg.getEnableOcrValidationForFile()).thenReturn(List.of(FileType.PDF));
        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.just(context.getPaperTrackings()));

        // Act
        StepVerifier.create(dematValidator.validateDemat(context))
                .verifyComplete();

        // Assert
        ArgumentCaptor<PaperTrackings> captor = ArgumentCaptor.forClass(PaperTrackings.class);
        verifyNoInteractions(safeStorageClient);
        verify(paperTrackingsDAO, times(1)).updateItem(any(), captor.capture());
        PaperTrackings paperTrackingsToUpdate = captor.getValue();
        assertTrue(paperTrackingsToUpdate.getValidationFlow().getOcrEnabled());
        assertNull(paperTrackingsToUpdate.getOcrRequestId());
        assertNull(paperTrackingsToUpdate.getValidationFlow().getOcrRequestTimestamp());
        verify(ocrMomProducer, never()).push(any(OcrEvent.class));
    }

    @Test
    void validateDemat_OcrDisabled_UpdatesItemAndDoesNotPushEvent() {
        // Arrange
        when(cfg.getEnableOcrValidationFor()).thenReturn(List.of(ProductType.RIR));
        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.empty());
        PaperStatus paperStatus = new PaperStatus();
        paperStatus.setValidatedEvents(List.of(getEvent("RECRN005C", null), getEvent("RECRN005A", null), getEvent("RECRN005B", "Plico")));
        context.getPaperTrackings().setPaperStatus(paperStatus);

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
        when(cfg.getEnableOcrValidationFor()).thenReturn(List.of(ProductType.AR));
        when(cfg.getEnableOcrValidationForFile()).thenReturn(List.of(FileType.PDF));

        when(safeStorageClient.getSafeStoragePresignedUrl(any())).thenReturn(Mono.just("presigned-url"));
        PaperStatus paperStatus = new PaperStatus();
        paperStatus.setValidatedEvents(List.of(getEvent("RECRN005C", null), getEvent("RECRN005A", null), getEvent("RECRN005B", "Plico")));
        context.getPaperTrackings().setPaperStatus(paperStatus);

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