package it.pagopa.pn.papertracker.utils;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.middleware.msclient.SafeStorageClient;
import it.pagopa.pn.papertracker.middleware.queue.model.OcrEvent;
import it.pagopa.pn.papertracker.middleware.queue.producer.OcrMomProducer;
import it.pagopa.pn.papertracker.model.FileType;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.model.OcrStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static it.pagopa.pn.papertracker.model.EventStatusCodeEnum.RECAG012;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OcrUtilityTest {
    @Mock
    OcrMomProducer ocrMomProducer;
    @Mock
    SafeStorageClient safeStorageClient;
    @Mock
    PnPaperTrackerConfigs cfg;
    @Mock
    PaperTrackingsDAO paperTrackingsDAO;

    @InjectMocks
    OcrUtility ocrUtility;

    private HandlerContext context;
    private PaperTrackings paperTrackings;

    @BeforeEach
    void setUp() {
        ocrUtility = new OcrUtility(ocrMomProducer, safeStorageClient, cfg, paperTrackingsDAO);
        context = new HandlerContext();
        paperTrackings = new PaperTrackings();
        paperTrackings.setTrackingId("trackingId");
        paperTrackings.setProductType(ProductType.AR.getValue());
        paperTrackings.setValidationConfig(new ValidationConfig());
        paperTrackings.setPaperStatus(new PaperStatus());
        paperTrackings.setEvents(new ArrayList<>());
        context.setPaperTrackings(paperTrackings);
    }

    @Test
    void checkAndSendToOcr_OcrEnabled_ValidAttachments() {
        paperTrackings.getValidationConfig().setOcrEnabled(OcrStatusEnum.RUN);
        Map<String, List<Attachment>> attachments = new HashMap<>();
        Attachment att = new Attachment();
        att.setUri("uri.pdf");
        att.setDocumentType("ARCAD");
        attachments.put("eventId", List.of(att));
        Event event = new Event();
        event.setStatusCode(RECAG012.name());
        event.setStatusTimestamp(Instant.now());
        when(cfg.getEnableOcrValidationForFile()).thenReturn(List.of(FileType.PDF));
        when(safeStorageClient.getSafeStoragePresignedUrl("uri.pdf")).thenReturn(Mono.just("presigned-url-1"));
        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.just(paperTrackings));

        StepVerifier.create(ocrUtility.checkAndSendToOcr(event, attachments, context))
                .expectNext(true)
                .verifyComplete();
        verify(ocrMomProducer, times(1)).push(any(OcrEvent.class));
        verify(paperTrackingsDAO, times(1)).updateItem(any(), any());
    }

    @Test
    void checkAndSendToOcr_OcrDisabled() {
        paperTrackings.getValidationConfig().setOcrEnabled(OcrStatusEnum.DISABLED);
        Map<String, List<Attachment>> attachments = new HashMap<>();
        Event event = new Event();
        event.setStatusCode(RECAG012.name());
        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.just(paperTrackings));

        StepVerifier.create(ocrUtility.checkAndSendToOcr(event, attachments, context))
                .expectNext(false)
                .verifyComplete();
        verifyNoInteractions(ocrMomProducer);
        verify(paperTrackingsDAO, times(1)).updateItem(any(), any());
    }

    @Test
    void checkAndSendToOcr_OcrEnabled_NoValidAttachments() {
        paperTrackings.getValidationConfig().setOcrEnabled(OcrStatusEnum.RUN);
        Map<String, List<Attachment>> attachments = new HashMap<>();
        Attachment att = new Attachment();
        att.setUri("uri.txt");
        att.setDocumentType("ARCAD");
        attachments.put("eventId", List.of(att));
        Event event = new Event();
        event.setStatusCode(RECAG012.name());
        when(cfg.getEnableOcrValidationForFile()).thenReturn(List.of(FileType.PDF));
        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.just(paperTrackings));

        StepVerifier.create(ocrUtility.checkAndSendToOcr(event, attachments, context))
                .expectNext(false)
                .verifyComplete();
        verifyNoInteractions(ocrMomProducer);
        verify(paperTrackingsDAO, times(1)).updateItem(any(), any());
    }

    @Test
    void checkAndSendToOcr_OcrEnabled_EmptyAttachments() {
        paperTrackings.getValidationConfig().setOcrEnabled(OcrStatusEnum.RUN);
        Event event = new Event();
        event.setStatusCode(RECAG012.name());
        Map<String, List<Attachment>> attachments = new HashMap<>();
        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.just(paperTrackings));

        StepVerifier.create(ocrUtility.checkAndSendToOcr(event, attachments, context))
                .expectNext(false)
                .verifyComplete();
        verifyNoInteractions(ocrMomProducer);
        verify(paperTrackingsDAO, times(1)).updateItem(any(), any());
    }

}