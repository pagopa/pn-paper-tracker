package it.pagopa.pn.papertracker.service.handler_step.RIR;

import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.model.DocumentTypeEnum;
import it.pagopa.pn.papertracker.model.HandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

class SequenceValidatorRirTest {

    @Mock
    private PaperTrackingsDAO paperTrackingsDAO;

    private SequenceValidatorRir sequenceValidatorRir;

    @InjectMocks
    private HandlerContext context;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        PaperProgressStatusEvent paperProgressStatusEvent = new PaperProgressStatusEvent();
        paperProgressStatusEvent.setStatusCode("RECRI003C");
        PaperTrackings paperTrackings = getPaperTrackings();
        context.setPaperTrackings(paperTrackings);
        context.setPaperProgressStatusEvent(paperProgressStatusEvent);
        sequenceValidatorRir = new SequenceValidatorRir(paperTrackingsDAO);
    }

    private PaperTrackings getPaperTrackings() {
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setPaperStatus(new PaperStatus());
        paperTrackings.setValidationFlow(new ValidationFlow());
        return paperTrackings;
    }

    @Test
    void validateSequenceValidMultipleFlow1() {

        //Arrange
        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();
        context.getPaperTrackings().setEvents(List.of(
                buildEvent("RECRI001", timestamp.plusSeconds(2), businessTimestamp.plusSeconds(1), "", "", null),
                buildEvent("RECRI002", timestamp.plusSeconds(3), businessTimestamp.plusSeconds(2), "", "", null),
                buildEvent("RECRI001", timestamp.plusSeconds(4), businessTimestamp.plusSeconds(3), "", "", null),
                buildEvent("RECRI002", timestamp.plusSeconds(4), businessTimestamp.plusSeconds(4), "", "", null),
                buildEvent("RECRI003A", timestamp.plusSeconds(4), businessTimestamp.plusSeconds(5), "", "", null),
                buildEvent("RECRI003B", timestamp.plusSeconds(4), businessTimestamp.plusSeconds(6), "", "", null),
                buildEvent("RECRI003A", timestamp.plusSeconds(4), businessTimestamp.plusSeconds(7), "", "", null),
                buildEvent("RECRI003A", timestamp.plusSeconds(5), businessTimestamp.plusSeconds(8), "", "", null),
                buildEvent("RECRI003B", timestamp.plusSeconds(5), businessTimestamp.plusSeconds(9), "", "", List.of(DocumentTypeEnum.AR.getValue())),
                buildEvent("RECRI003C", timestamp.plusSeconds(5), businessTimestamp.plusSeconds(11), "", "", null)
        ));

        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(sequenceValidatorRir.execute(context))
                .verifyComplete();

        // Assert
        verify(paperTrackingsDAO, times(1)).updateItem(any(), any());
    }

    @Test
    void validateSequenceValidFlow003() {
        //Arrange
        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();
        context.getPaperTrackings().setEvents(List.of(
                buildEvent("RECRI001", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRI002", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRI003A", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRI003B", timestamp, businessTimestamp.plusSeconds(1), "REG123", "", List.of(DocumentTypeEnum.AR.getValue())),
                buildEvent("RECRI003C", timestamp, businessTimestamp.plusSeconds(2), "REG123", "", null)
        ));

        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(sequenceValidatorRir.execute(context))
                .verifyComplete();

        // Assert
        verify(paperTrackingsDAO, times(1)).updateItem(any(), any());
    }


    @Test
    void validateSequenceValidFlow004() {
        // Arrange
        context.getPaperProgressStatusEvent().setStatusCode("RECRI004C");
        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();
        context.getPaperTrackings().setEvents(List.of(
                buildEvent("RECRI001", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRI002", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRI004A", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRI004B", timestamp, businessTimestamp.plusSeconds(1), "REG123", "F01", List.of(DocumentTypeEnum.PLICO.getValue())),
                buildEvent("RECRI004C", timestamp, businessTimestamp.plusSeconds(2), "REG123", "", null)
        ));
        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(sequenceValidatorRir.execute(context))
                .verifyComplete();

        // Assert
        verify(paperTrackingsDAO, times(1)).updateItem(any(), any());
    }

    @Test
    void validateSequenceValidFlow004MultiDocumentFailure() {
        // Arrange
        context.getPaperProgressStatusEvent().setStatusCode("RECRI004C");

        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();

        context.getPaperTrackings().setEvents(List.of(
                buildEvent("RECRI001", timestamp, businessTimestamp, "", "", null),
                buildEvent("RECRI002", timestamp, businessTimestamp, "", "", null),
                buildEvent("RECRI004A", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRI004B", timestamp, businessTimestamp.plusSeconds(1), "REG123", "F01", List.of(DocumentTypeEnum.INDAGINE.getValue())),
                buildEvent("RECRI004C", timestamp, businessTimestamp.plusSeconds(2), "REG123", "", null)
        ));

        // Act & Assert
        StepVerifier.create(sequenceValidatorRir.execute(context))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException &&
                        throwable.getMessage().contains("Missed required attachments for the sequence validation: [Plico]"))
                .verify();
    }


    @Test
    void validateSequenceInvalidEventCount() {
        // Arrange
        context.getPaperTrackings().setEvents(List.of(
                buildEvent("RECRI003A", Instant.now(), Instant.now(), "REG123", "", null)
        ));

        // Act & Assert
        StepVerifier.create(sequenceValidatorRir.execute(context))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException &&
                        throwable.getMessage().contains("Necessary status code not found in events"))
                .verify();
    }

    @Test
    void invalidBusinessTimestamp() {
        // Arrange
        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();

        context.getPaperTrackings().setEvents(List.of(
                buildEvent("RECRI001", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRI002", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRI003A", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRI003B", timestamp.plusSeconds(1), businessTimestamp.plusSeconds(1), "REG123", "", List.of(DocumentTypeEnum.AR.getValue())),
                buildEvent("RECRI003C", timestamp.plusSeconds(2), businessTimestamp.plusSeconds(2), "REG123", "", null)
        ));

        // Act & Assert
        StepVerifier.create(sequenceValidatorRir.execute(context))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException &&
                        throwable.getMessage().contains("Invalid business timestamps"))
                .verify();
    }

    @Test
    void validateSequenceInvalidSequenceSize() {
        // Arrange
        context.getPaperProgressStatusEvent().setStatusCode("RECRI004C");

        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();

        context.getPaperTrackings().setEvents(List.of(
                buildEvent("RECRI003A", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRI003B", timestamp, businessTimestamp, "REG123", "", List.of(DocumentTypeEnum.AR.getValue())),
                buildEvent("RECRI004C", timestamp, businessTimestamp, "REG123", "", null)
        ));

        // Act & Assert
        StepVerifier.create(sequenceValidatorRir.execute(context))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException &&
                        throwable.getMessage().contains("Necessary status code not found in events"))
                .verify();
    }


    @Test
    void validateSequenceInvalidRegisteredLetterCode() {
        // Arrange
        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();

        context.getPaperTrackings().setEvents(List.of(
                buildEvent("RECRI001", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRI002", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRI003A", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRI003B", timestamp, businessTimestamp.plusSeconds(1), "REG444", "", List.of(DocumentTypeEnum.AR.getValue())),
                buildEvent("RECRI003C", timestamp, businessTimestamp.plusSeconds(2), "REG123", "", null)
        ));

        // Act & Assert
        StepVerifier.create(sequenceValidatorRir.execute(context))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException &&
                        throwable.getMessage().contains("Registered letter codes do not match in sequence"))
                .verify();
    }
    
    private Event buildEvent(String statusCode, Instant statusTimestamp, Instant requestTimestamp, String registeredLetterCode, String deliveryFailureCause, List<String> attachmentTypes) {
        Event event = new Event();
        event.setAttachments(new ArrayList<>());
        event.setStatusCode(statusCode);
        event.setRegisteredLetterCode(registeredLetterCode);
        event.setStatusTimestamp(statusTimestamp);
        event.setRequestTimestamp(requestTimestamp);
        event.setDeliveryFailureCause(deliveryFailureCause);

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