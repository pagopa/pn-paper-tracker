package it.pagopa.pn.papertracker.service.handler_step.AR;

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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

class SequenceValidatorArTest {

    @Mock
    private PaperTrackingsDAO paperTrackingsDAO;

    private SequenceValidatorAr sequenceValidatorAr;

    @InjectMocks
    private HandlerContext context;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        PaperProgressStatusEvent paperProgressStatusEvent = new PaperProgressStatusEvent();
        paperProgressStatusEvent.setStatusCode("RECRN001C");
        context.setPaperProgressStatusEvent(paperProgressStatusEvent);
        PaperTrackings paperTrackings = getPaperTrackings();
        context.setPaperTrackings(paperTrackings);
        sequenceValidatorAr = new SequenceValidatorAr(paperTrackingsDAO);
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
        context.getPaperProgressStatusEvent().setStatusCode("RECRN003C");
        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();
        context.getPaperTrackings().setEvents(List.of(
                buildEvent("RECRN010", timestamp.plusSeconds(2), businessTimestamp.plusSeconds(1), "REG1", "", null),
                buildEvent("RECRN011", timestamp.plusSeconds(3), businessTimestamp.plusSeconds(2), "REG1", "", null),
                buildEvent("RECRN010", timestamp.plusSeconds(4), businessTimestamp.plusSeconds(3), "REG1", "", null),
                buildEvent("RECRN011", timestamp.plusSeconds(4), businessTimestamp.plusSeconds(4), "REG1", "", null),
                buildEvent("RECRN003A", timestamp.plusSeconds(4), businessTimestamp.plusSeconds(5), "REG1", "", null),
                buildEvent("RECRN004A", timestamp.plusSeconds(4), businessTimestamp.plusSeconds(6), "REG1", "", null),
                buildEvent("RECRN003B", timestamp.plusSeconds(4), businessTimestamp.plusSeconds(7), "REG1", "", List.of(DocumentTypeEnum.AR.getValue())),
                buildEvent("RECRN003A", timestamp.plusSeconds(5), businessTimestamp.plusSeconds(8), "REG1", "", null),
                buildEvent("RECRN003B", timestamp.plusSeconds(5), businessTimestamp.plusSeconds(9), "REG1", "", List.of(DocumentTypeEnum.AR.getValue())),
                buildEvent("RECRN003C", timestamp.plusSeconds(5), businessTimestamp.plusSeconds(11), "REG1", "", null)
        ));

        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(sequenceValidatorAr.execute(context))
                .verifyComplete();

        // Assert
        verify(paperTrackingsDAO, times(1)).updateItem(any(), any());
    }

    @Test
    void sequenceRECRN002FDocumentsSplitted() {

        //Arrange
        context.getPaperProgressStatusEvent().setStatusCode("RECRN002F");

        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();
        context.getPaperTrackings().setEvents(List.of(
                buildEvent("RECRN002D", timestamp.plusSeconds(1), businessTimestamp.plusSeconds(1), "REG1", "M01", null),
                buildEvent("RECRN002E", timestamp.plusSeconds(1), businessTimestamp.plusSeconds(2), "REG1", "", List.of(DocumentTypeEnum.INDAGINE.getValue())),
                buildEvent("RECRN002E", timestamp.plusSeconds(1), businessTimestamp.plusSeconds(4), "REG1", "", List.of(DocumentTypeEnum.PLICO.getValue(), DocumentTypeEnum.INDAGINE.getValue())),
                buildEvent("RECRN002E", timestamp.plusSeconds(1), businessTimestamp.plusSeconds(5), "REG1", "", List.of(DocumentTypeEnum.PLICO.getValue())),
                buildEvent("RECRN002F", timestamp.plusSeconds(1), businessTimestamp.plusSeconds(6), "REG1", "", null)
        ));

        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(sequenceValidatorAr.execute(context))
                .verifyComplete();

        // Assert
        verify(paperTrackingsDAO, times(1)).updateItem(any(), any());
    }

    @Test
    void sequenceRECRN002FDocumentsSplittedWithInvalidAttachments() {

        //Arrange
        context.getPaperProgressStatusEvent().setStatusCode("RECRN002F");

        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();
        context.getPaperTrackings().setEvents(List.of(
                buildEvent("RECRN002D", timestamp.plusSeconds(1), businessTimestamp.plusSeconds(1), "", "M01", null),
                buildEvent("RECRN002E", timestamp.plusSeconds(1), businessTimestamp.plusSeconds(2), "", "", List.of(DocumentTypeEnum.INDAGINE.getValue())),
                buildEvent("RECRN002E", timestamp.plusSeconds(1), businessTimestamp.plusSeconds(4), "", "", List.of(DocumentTypeEnum.PLICO.getValue(), DocumentTypeEnum.AR.getValue())),
                buildEvent("RECRN002E", timestamp.plusSeconds(1), businessTimestamp.plusSeconds(5), "", "", List.of(DocumentTypeEnum.PLICO.getValue())),
                buildEvent("RECRN002F", timestamp.plusSeconds(1), businessTimestamp.plusSeconds(6), "", "", null)
        ));

        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(sequenceValidatorAr.execute(context))
                .verifyErrorMatches(throwable -> throwable.getMessage()
                        .contains("Event RECRN002E contains invalid attachments: [AR]"));

        // Assert
        verify(paperTrackingsDAO, times(0)).updateItem(any(), any());
    }


    @Test
    void validateSequenceValidFlowABC() {
        //Arrange
        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();
        context.getPaperTrackings().setEvents(List.of(
                buildEvent("RECRN001A", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRN001B", timestamp, businessTimestamp.plusSeconds(1), "REG123", "", List.of(DocumentTypeEnum.AR.getValue())),
                buildEvent("RECRN001C", timestamp, businessTimestamp.plusSeconds(2), "REG123", "", null)
        ));

        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(sequenceValidatorAr.execute(context))
                .verifyComplete();

        // Assert
        verify(paperTrackingsDAO, times(1)).updateItem(any(), any());
    }

    @Test
    void validateSequenceValidFlowABCAllWithDocuments() {
        //Arrange
        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();
        context.getPaperTrackings().setEvents(List.of(
                buildEvent("RECRN001A", timestamp, businessTimestamp, "REG123", "", List.of(DocumentTypeEnum.AR.getValue())),
                buildEvent("RECRN001B", timestamp, businessTimestamp.plusSeconds(1), "REG123", "", List.of(DocumentTypeEnum.AR.getValue())),
                buildEvent("RECRN001C", timestamp, businessTimestamp.plusSeconds(2), "REG123", "", List.of(DocumentTypeEnum.AR.getValue()))
        ));

        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(sequenceValidatorAr.execute(context))
                .verifyErrorMatches(throwable -> throwable.getMessage().contains("Event RECRN001C contains invalid attachments: [AR]"));

        // Assert
        verify(paperTrackingsDAO, times(0)).updateItem(any(), any());
    }

    @Test
    void validateSequenceValidFlowDEF() {
        // Arrange
        context.getPaperProgressStatusEvent().setStatusCode("RECRN002F");

        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();
        context.getPaperTrackings().setEvents(List.of(
                buildEvent("RECRN002D", timestamp, businessTimestamp, "REG123", "M03", null),
                buildEvent("RECRN002E", timestamp, businessTimestamp.plusSeconds(1), "REG123", "", List.of(DocumentTypeEnum.PLICO.getValue(), DocumentTypeEnum.INDAGINE.getValue())),
                buildEvent("RECRN002F", timestamp, businessTimestamp.plusSeconds(2), "REG123", "", null)
        ));
        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(sequenceValidatorAr.execute(context))
                .verifyComplete();

        // Assert
        verify(paperTrackingsDAO, times(1)).updateItem(any(), any());
    }

    @Test
    void validateSequenceValidFlowDEFMultiDocumentFailure() {
        // Arrange
        context.getPaperProgressStatusEvent().setStatusCode("RECRN002F");

        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();

        context.getPaperTrackings().setEvents(List.of(
                buildEvent("RECRN002D", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRN002E", timestamp, businessTimestamp.plusSeconds(1), "REG123", "M01", List.of(DocumentTypeEnum.AR.getValue())),
                buildEvent("RECRN002F", timestamp, businessTimestamp.plusSeconds(2), "REG123", "", null)
        ));

        // Act & Assert
        StepVerifier.create(sequenceValidatorAr.execute(context))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException &&
                        throwable.getMessage().contains("Missed required attachments for the sequence validation: [Plico]"))
                .verify();
    }

    @Test
    void validateSequenceValidFlowDEFMultiDocumentDuplicateFailure() {
        // Arrange
        context.getPaperProgressStatusEvent().setStatusCode("RECRN002F");

        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();

        context.getPaperTrackings().setEvents(List.of(
                buildEvent("RECRN002D", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRN002E", timestamp, businessTimestamp.plusSeconds(1), "REG123", "M01", List.of(DocumentTypeEnum.AR.getValue(), DocumentTypeEnum.AR.getValue())),
                buildEvent("RECRN002F", timestamp, businessTimestamp.plusSeconds(2), "REG123", "", null)
        ));

        // Act & Assert
        StepVerifier.create(sequenceValidatorAr.execute(context))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException &&
                        throwable.getMessage().contains("Missed required attachments for the sequence validation: [Plico]"))
                .verify();
    }

    @Test
    void validateSequenceInvalidEventCount() {
        // Arrange
        context.getPaperTrackings().setEvents(List.of(
                buildEvent("RECRN001A", Instant.now(), Instant.now(), "REG123", "", null)
        ));

        // Act & Assert
        StepVerifier.create(sequenceValidatorAr.execute(context))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException &&
                        throwable.getMessage().contains("Necessary status code not found in events"))
                .verify();
    }

    @Test
    void invalidBusinessTimestamp() {
        // Arrange
        context.getPaperProgressStatusEvent().setStatusCode("RECRN002C");

        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();

        context.getPaperTrackings().setEvents(List.of(
                buildEvent("RECRN002A", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRN002B", timestamp, businessTimestamp.plusSeconds(1), "REG123", "TESTERR", List.of(DocumentTypeEnum.PLICO.getValue())),
                buildEvent("RECRN002B", timestamp.plus(1, ChronoUnit.DAYS), businessTimestamp.plusSeconds(2), "REG123", "TESTERR", List.of(DocumentTypeEnum.PLICO.getValue())),
                buildEvent("RECRN002C", timestamp, businessTimestamp.plusSeconds(3), "REG123", "", null)
        ));

        // Act & Assert
        StepVerifier.create(sequenceValidatorAr.execute(context))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException &&
                        throwable.getMessage().contains("Invalid business timestamps"))
                .verify();
    }

    @Test
    void validateSequenceInvalidSequenceSize() {
        // Arrange
        context.getPaperProgressStatusEvent().setStatusCode("RECRN002C");

        Instant timestamp = Instant.now();
        context.getPaperTrackings().setEvents(List.of(
                buildEvent("RECRN001A", timestamp, Instant.now(), "REG123", "", null),
                buildEvent("RECRN001B", timestamp, Instant.now(), "REG123", "", null),
                buildEvent("RECRN002C", timestamp, Instant.now(), "REG123", "", null)
        ));

        // Act & Assert
        StepVerifier.create(sequenceValidatorAr.execute(context))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException &&
                        throwable.getMessage().contains("Necessary status code not found in events"))
                .verify();
    }

    @Test
    void validateSequenceInvalidSequenceLetters() {
        // Arrange
        context.getPaperProgressStatusEvent().setStatusCode("RECRN002F");

        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();

        context.getPaperTrackings().setEvents(List.of(
                buildEvent("RECRN001A", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRN001B", timestamp, businessTimestamp.plusSeconds(1), "REG123", "", null),
                buildEvent("RECRN002F", timestamp, businessTimestamp.plusSeconds(2), "REG123", "", null)
        ));

        // Act & Assert
        StepVerifier.create(sequenceValidatorAr.execute(context))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException &&
                        throwable.getMessage().contains("Necessary status code not found in events"))
                .verify();
    }

    @Test
    void validateSequenceInvalidSequenceCodes() {
        // Arrange
        context.getPaperProgressStatusEvent().setStatusCode("RECRN002C");

        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();

        context.getPaperTrackings().setEvents(List.of(
                buildEvent("RECRN001A", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRN001B", timestamp, businessTimestamp.plusSeconds(1), "REG123", "", null),
                buildEvent("RECRN002C", timestamp, businessTimestamp.plusSeconds(2), "REG123", "", null)
        ));

        // Act & Assert
        StepVerifier.create(sequenceValidatorAr.execute(context))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException &&
                        throwable.getMessage().contains("Necessary status code not found in events"))
                .verify();
    }

    @Test
    void validateSequenceInvalidTimestamps() {
        // Arrange
        Instant businessTimestamp = Instant.now();

        context.getPaperTrackings().setEvents(List.of(
                buildEvent("RECRN001A", Instant.now(), businessTimestamp, "REG123", "", null),
                buildEvent("RECRN001B", Instant.now().plusSeconds(10), businessTimestamp.plusSeconds(1), "REG123", "", List.of(DocumentTypeEnum.AR.getValue())),
                buildEvent("RECRN001C", Instant.now(), businessTimestamp.plusSeconds(2), "REG123", "", null)
        ));

        // Act & Assert
        StepVerifier.create(sequenceValidatorAr.execute(context))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException &&
                        throwable.getMessage().contains("Invalid business timestamps"))
                .verify();
    }

    @Test
    void validateSequenceInvalidRegisteredLetterCode() {
        // Arrange
        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();

        context.getPaperTrackings().setEvents(List.of(
                buildEvent("RECRN001A", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRN001B", timestamp, businessTimestamp.plusSeconds(1), "REG444", "", List.of(DocumentTypeEnum.AR.getValue())),
                buildEvent("RECRN001C", timestamp, businessTimestamp.plusSeconds(2), "REG123", "", null)
        ));

        // Act & Assert
        StepVerifier.create(sequenceValidatorAr.execute(context))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException &&
                        throwable.getMessage().contains("Registered letter codes do not match in sequence"))
                .verify();
    }

    @Test
    void validateSequenceRegisteredLetterCodeNotFound() {
        // Arrange
        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();

        context.getPaperTrackings().setEvents(List.of(
                buildEvent("RECRN001A", timestamp, businessTimestamp, null, "", null),
                buildEvent("RECRN001B", timestamp, businessTimestamp.plusSeconds(1), "REG123", "", List.of(DocumentTypeEnum.AR.getValue())),
                buildEvent("RECRN001C", timestamp, businessTimestamp.plusSeconds(2), null, "", null)
        ));

        // Act & Assert
        StepVerifier.create(sequenceValidatorAr.execute(context))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException &&
                        throwable.getMessage().contains("Registered letter code is null or empty in one or more events"))
                .verify();
    }

    @Test
    void validateSequenceInvalidNullDeliveryFailureCause() {
        // Arrange
        context.getPaperProgressStatusEvent().setStatusCode("RECRN002C");

        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();

        context.getPaperTrackings().setEvents(List.of(
                buildEvent("RECRN002A", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRN002B", timestamp, businessTimestamp.plusSeconds(1), "REG123", "", List.of(DocumentTypeEnum.PLICO.getValue())),
                buildEvent("RECRN002C", timestamp, businessTimestamp.plusSeconds(2), "REG123", "", null)
        ));

        // Act & Assert
        StepVerifier.create(sequenceValidatorAr.execute(context))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException &&
                        throwable.getMessage().contains("Invalid deliveryFailureCause: "))
                .verify();
    }

    @Test
    void validateSequenceInvalidDeliveryFailureCause() {
        // Arrange
        context.getPaperProgressStatusEvent().setStatusCode("RECRN002C");

        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();

        context.getPaperTrackings().setEvents(List.of(
                buildEvent("RECRN002A", timestamp, businessTimestamp, "REG123", "TESTERR", null),
                buildEvent("RECRN002B", timestamp, businessTimestamp.plusSeconds(1), "REG123", "", List.of(DocumentTypeEnum.PLICO.getValue())),
                buildEvent("RECRN002C", timestamp, businessTimestamp.plusSeconds(2), "REG123", "", null)
        ));

        // Act & Assert
        StepVerifier.create(sequenceValidatorAr.execute(context))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException &&
                        throwable.getMessage().contains("Invalid deliveryFailureCause: TESTERR"))
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