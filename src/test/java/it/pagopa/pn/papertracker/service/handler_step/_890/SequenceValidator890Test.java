package it.pagopa.pn.papertracker.service.handler_step._890;

import it.pagopa.pn.papertracker.config.SequenceConfiguration;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.model.DocumentTypeEnum;
import it.pagopa.pn.papertracker.model.HandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SequenceValidator890Test {

    @Mock
    private PaperTrackingsDAO paperTrackingsDAO;

    private final SequenceConfiguration sequenceConfiguration = new SequenceConfiguration();

    private SequenceValidator890 sequenceValidator890;

    private HandlerContext context;

    @BeforeEach
    void setUp() {
        sequenceValidator890 = new SequenceValidator890(sequenceConfiguration, paperTrackingsDAO);
        context = new HandlerContext();
        context.setPaperProgressStatusEvent(new PaperProgressStatusEvent());
    }

    @Test
    void executeWithStockStatusFalse() {
        // Arrange
        PaperTrackings paperTrackings = getPaperTrackings();
        context.getPaperProgressStatusEvent().setStatusCode("RECAG003C");
        context.setPaperTrackings(paperTrackings);
        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(sequenceValidator890.execute(context))
                .verifyComplete();

        // Assert
        verify(paperTrackingsDAO, times(1)).updateItem(any(), any());
    }

    @Test
    void executeWithStockStatusTrueAndStateDONE() {
        // Arrange
        PaperTrackings paperTrackings = getPaperTrackings();
        paperTrackings.setState(PaperTrackingsState.DONE);
        context.getPaperProgressStatusEvent().setStatusCode("RECAG005C");
        context.setPaperTrackings(paperTrackings);
        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(sequenceValidator890.execute(context))
                .verifyComplete();

        // Assert
        verify(paperTrackingsDAO, times(1)).updateItem(any(), any());
    }

    @Test
    void executeWithStockStatusTrueAndStateAWAITING_REFINEMENT() {
        // Arrange
        PaperTrackings paperTrackings = getPaperTrackings();
        paperTrackings.setState(PaperTrackingsState.AWAITING_REFINEMENT);
        context.getPaperProgressStatusEvent().setStatusCode("RECAG005C");
        context.setPaperTrackings(paperTrackings);

        // Act
        StepVerifier.create(sequenceValidator890.execute(context))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException)
                .verify();

        // Assert
        verify(paperTrackingsDAO, never()).updateItem(any(), any());
    }

    @Test
    void executeWithStockStatusTrueAndStateAWAITING_OCR() {
        // Arrange
        PaperTrackings paperTrackings = getPaperTrackings();
        paperTrackings.setState(PaperTrackingsState.AWAITING_OCR);
        context.getPaperProgressStatusEvent().setStatusCode("RECAG005C");
        context.setPaperTrackings(paperTrackings);
        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(sequenceValidator890.execute(context))
                .verifyComplete();

        // Assert
        ArgumentCaptor<PaperTrackings> captor = ArgumentCaptor.forClass(PaperTrackings.class);
        verify(paperTrackingsDAO, times(1)).updateItem(any(), captor.capture());
        assertEquals(BusinessState.AWAITING_REFINEMENT_OCR, captor.getValue().getBusinessState());
        assertTrue(context.isStopExecution());
    }

    @Test
    void executeWithStockStatusTrueAndStateKO() {
        // Arrange
        PaperTrackings paperTrackings = getPaperTrackings();
        paperTrackings.setState(PaperTrackingsState.KO);
        context.getPaperProgressStatusEvent().setStatusCode("RECAG005C");
        context.setPaperTrackings(paperTrackings);

        // Act
        StepVerifier.create(sequenceValidator890.execute(context))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException)
                .verify();

        // Assert
        verify(paperTrackingsDAO, never()).updateItem(any(), any());
    }

    private PaperTrackings getPaperTrackings() {
        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setPaperStatus(new PaperStatus());
        paperTrackings.setValidationFlow(new ValidationFlow());
        paperTrackings.setEvents(List.of(
                buildEvent("RECAG001A", timestamp, businessTimestamp, null),
                buildEvent("RECAG001B", timestamp, businessTimestamp.plusSeconds(1), List.of(DocumentTypeEnum._23L.getValue())),
                buildEvent("RECAG001C", timestamp, businessTimestamp.plusSeconds(2), null)
        ));
        return paperTrackings;
    }

    private Event buildEvent(String statusCode, Instant statusTimestamp, Instant requestTimestamp, List<String> attachmentTypes) {
        Event event = new Event();
        event.setAttachments(new ArrayList<>());
        event.setStatusCode(statusCode);
        event.setRegisteredLetterCode("REG123");
        event.setStatusTimestamp(statusTimestamp);
        event.setRequestTimestamp(requestTimestamp);
        event.setDeliveryFailureCause("");

        if (!CollectionUtils.isEmpty(attachmentTypes)) {
            for (String type : attachmentTypes) {
                Attachment attachment = new Attachment();
                attachment.setDocumentType(type);
                event.getAttachments().add(attachment);
            }
        }

        return event;
    }
}
