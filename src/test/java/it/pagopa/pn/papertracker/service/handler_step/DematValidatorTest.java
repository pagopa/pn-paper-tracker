package it.pagopa.pn.papertracker.service.handler_step;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.exception.PaperTrackerException;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.middleware.msclient.SafeStorageClient;
import it.pagopa.pn.papertracker.middleware.queue.model.OcrEvent;
import it.pagopa.pn.papertracker.middleware.queue.producer.OcrMomProducer;
import it.pagopa.pn.papertracker.model.FileType;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.generic.DematValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
        PaperStatus paperStatus = new PaperStatus();
        paperStatus.setValidatedEvents(List.of(getEvent("RECRN005C", null), getEvent("RECRN005A", null), getEvent("RECRN005B", "Plico")));
        context.getPaperTrackings().setPaperStatus(paperStatus);

        when(cfg.getEnableOcrValidationFor()).thenReturn(List.of(ProductType.AR));
        when(cfg.getEnableOcrValidationForFile()).thenReturn(List.of(FileType.PDF));
        when(safeStorageClient.getSafeStoragePresignedUrl(any())).thenReturn(Mono.just("presigned-url"));
        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.just(context.getPaperTrackings()));

        StepVerifier.create(dematValidator.validateDemat(context))
                .verifyComplete();

        verify(safeStorageClient, times(1)).getSafeStoragePresignedUrl(any());
        verify(paperTrackingsDAO, times(1)).updateItem(any(), any());
        verify(ocrMomProducer, times(1)).push(any(OcrEvent.class));
    }

    @Test
    void validateDemat_OcrEnabled_UpdatesItemAndPushesEvent2() {
        PaperStatus paperStatus = new PaperStatus();
        paperStatus.setValidatedEvents(List.of(getEvent("RECRN002F", null), getEvent("RECRN002D", null), getEvent("RECRN002E", "AR")));
        context.getPaperTrackings().setPaperStatus(paperStatus);

        when(cfg.getEnableOcrValidationFor()).thenReturn(List.of(ProductType.AR));
        when(cfg.getEnableOcrValidationForFile()).thenReturn(List.of(FileType.PDF));

        when(safeStorageClient.getSafeStoragePresignedUrl(any())).thenReturn(Mono.just("presigned-url"));
        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.just(context.getPaperTrackings()));

        StepVerifier.create(dematValidator.validateDemat(context))
                .verifyComplete();

        verify(safeStorageClient, times(1)).getSafeStoragePresignedUrl(any());
        verify(paperTrackingsDAO, times(1)).updateItem(any(), any());
        verify(ocrMomProducer, times(1)).push(any(OcrEvent.class));
    }

    @Test
    void validateDemat_OcrDisabled_UpdatesItemAndDoesNotPushEvent() {
        when(cfg.getEnableOcrValidationFor()).thenReturn(List.of(ProductType.RIR));
        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.empty());
        PaperStatus paperStatus = new PaperStatus();
        paperStatus.setValidatedEvents(List.of(getEvent("RECRN005C", null), getEvent("RECRN005A", null), getEvent("RECRN005B", "Plico")));
        context.getPaperTrackings().setPaperStatus(paperStatus);

        StepVerifier.create(dematValidator.validateDemat(context))
                .verifyComplete();

        verifyNoInteractions(safeStorageClient);
        verify(paperTrackingsDAO, times(1)).updateItem(any(), any());
        verify(ocrMomProducer, never()).push(any(OcrEvent.class));
    }

    @Test
    void validateDemat_UpdateItemThrowsError_PropagatesError() {
        when(cfg.getEnableOcrValidationFor()).thenReturn(List.of(ProductType.AR));
        when(cfg.getEnableOcrValidationForFile()).thenReturn(List.of(FileType.PDF));

        when(safeStorageClient.getSafeStoragePresignedUrl(any())).thenReturn(Mono.just("presigned-url"));
        PaperStatus paperStatus = new PaperStatus();
        paperStatus.setValidatedEvents(List.of(getEvent("RECRN005C", null), getEvent("RECRN005A", null), getEvent("RECRN005B", "Plico")));
        context.getPaperTrackings().setPaperStatus(paperStatus);

        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.error(new RuntimeException("DB error")));

        StepVerifier.create(dematValidator.validateDemat(context))
                .expectErrorMatches(e -> e instanceof PaperTrackerException && e.getMessage().contains("Error during Demat Validation"))
                .verify();

        verify(ocrMomProducer, times(1)).push(any(OcrEvent.class));
        verify(safeStorageClient, times(1)).getSafeStoragePresignedUrl(any());
        verify(paperTrackingsDAO, times(1)).updateItem(any(), any());
    }

}