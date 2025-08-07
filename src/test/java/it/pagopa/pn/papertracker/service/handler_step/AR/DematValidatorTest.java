package it.pagopa.pn.papertracker.service.handler_step.AR;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.exception.PaperTrackerException;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperStatus;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ValidationFlow;
import it.pagopa.pn.papertracker.middleware.queue.model.OcrEvent;
import it.pagopa.pn.papertracker.middleware.queue.producer.OcrMomProducer;
import it.pagopa.pn.papertracker.model.HandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

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
    }

    @Test
    void validateDemat_OcrEnabled_UpdatesItemAndPushesEvent() {
        when(cfg.isEnableOcrValidation()).thenReturn(true);
        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.just(context.getPaperTrackings()));

        StepVerifier.create(dematValidator.validateDemat(context))
                .verifyComplete();

        verify(paperTrackingsDAO, times(1)).updateItem(any(), any());
        verify(ocrMomProducer, times(1)).push(any(OcrEvent.class));
    }

    @Test
    void validateDemat_OcrDisabled_UpdatesItemAndDoesNotPushEvent() {
        when(cfg.isEnableOcrValidation()).thenReturn(false);
        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(dematValidator.validateDemat(context))
                .verifyComplete();

        verify(paperTrackingsDAO, times(1)).updateItem(any(), any());
        verify(ocrMomProducer, never()).push(any(OcrEvent.class));
    }

    @Test
    void validateDemat_UpdateItemThrowsError_PropagatesError() {
        when(cfg.isEnableOcrValidation()).thenReturn(true);
        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.error(new RuntimeException("DB error")));

        StepVerifier.create(dematValidator.validateDemat(context))
                .expectErrorMatches(e -> e instanceof PaperTrackerException && e.getMessage().contains("Error during Demat Validation"))
                .verify();

        verify(paperTrackingsDAO, times(1)).updateItem(any(), any());
    }

}