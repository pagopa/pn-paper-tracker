package it.pagopa.pn.papertracker.service.handler_step.AR;

import it.pagopa.pn.papertracker.config.SequenceConfiguration;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.model.DocumentTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

class SequenceValidatorTest {

    @Mock
    private PaperTrackingsDAO paperTrackingsDAO;

    private final SequenceConfiguration sequenceConfiguration = new SequenceConfiguration();

    private SequenceValidator sequenceValidator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        sequenceValidator = new SequenceValidator(paperTrackingsDAO, sequenceConfiguration);
    }

    @Test
    void validateSequenceValidMultipleFlow1() {

        //Arrange
        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setPaperStatus(new PaperStatus());
        paperTrackings.setValidationFlow(new ValidationFlow());
        paperTrackings.setEvents(List.of(
                buildEvent("RECRN010", timestamp.plusSeconds(2), businessTimestamp.plusSeconds(1), "", "", null),
                buildEvent("RECRN011", timestamp.plusSeconds(3), businessTimestamp.plusSeconds(2), "", "", null),
                buildEvent("RECRN010", timestamp.plusSeconds(4), businessTimestamp.plusSeconds(3), "", "", null),
                buildEvent("RECRN011", timestamp.plusSeconds(4), businessTimestamp.plusSeconds(4), "", "", null),
                buildEvent("RECRN003A", timestamp.plusSeconds(4), businessTimestamp.plusSeconds(5), "", "", null),
                buildEvent("RECRN004A", timestamp.plusSeconds(4), businessTimestamp.plusSeconds(6), "", "", null),
                buildEvent("RECRN003B", timestamp.plusSeconds(4), businessTimestamp.plusSeconds(7), "", "", List.of(DocumentTypeEnum.AR.getValue())),
                buildEvent("RECRN003A", timestamp.plusSeconds(5), businessTimestamp.plusSeconds(8), "", "", null),
                buildEvent("RECRN003B", timestamp.plusSeconds(5), businessTimestamp.plusSeconds(9), "", "", List.of(DocumentTypeEnum.AR.getValue())),
                buildEvent("RECRN003B", timestamp.plusSeconds(5), businessTimestamp.plusSeconds(10), "", "", List.of(DocumentTypeEnum.PLICO.getValue())),
                buildEvent("RECRN003C", timestamp.plusSeconds(5), businessTimestamp.plusSeconds(11), "", "", null)
        ));

        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(sequenceValidator.validateSequence(paperTrackings))
                .verifyComplete();

        // Assert
        verify(paperTrackingsDAO, times(1)).updateItem(any(), any());
    }

    @Test
    void sequenceRECRN002FDocumentsSplitted() {

        //Arrange
        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setPaperStatus(new PaperStatus());
        paperTrackings.setValidationFlow(new ValidationFlow());
        paperTrackings.setEvents(List.of(
                buildEvent("RECRN002D", timestamp.plusSeconds(1), businessTimestamp.plusSeconds(1), "", "", null),
                buildEvent("RECRN002E", timestamp.plusSeconds(1), businessTimestamp.plusSeconds(2), "", "M01", List.of(DocumentTypeEnum.INDAGINE.getValue())),
                buildEvent("RECRN002E", timestamp.plusSeconds(1), businessTimestamp.plusSeconds(3), "", "M01", List.of(DocumentTypeEnum.AR.getValue())),
                buildEvent("RECRN002E", timestamp.plusSeconds(1), businessTimestamp.plusSeconds(4), "", "M01", List.of(DocumentTypeEnum.PLICO.getValue(), DocumentTypeEnum.AR.getValue())),
                buildEvent("RECRN002E", timestamp.plusSeconds(1), businessTimestamp.plusSeconds(5), "", "M01", List.of(DocumentTypeEnum.PLICO.getValue())),
                buildEvent("RECRN002F", timestamp.plusSeconds(1), businessTimestamp.plusSeconds(6), "", "", null)
        ));

        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(sequenceValidator.validateSequence(paperTrackings))
                .verifyComplete();

        // Assert
        verify(paperTrackingsDAO, times(1)).updateItem(any(), any());
    }

    @Test
    void validateSequenceValidFlowABC() {
        //Arrange
        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setPaperStatus(new PaperStatus());
        paperTrackings.setValidationFlow(new ValidationFlow());
        paperTrackings.setEvents(List.of(
                buildEvent("RECRN001A", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRN001B", timestamp, businessTimestamp.plusSeconds(1), "REG123", "", List.of(DocumentTypeEnum.AR.getValue())),
                buildEvent("RECRN001C", timestamp, businessTimestamp.plusSeconds(2), "REG123", "", null)
        ));

        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(sequenceValidator.validateSequence(paperTrackings))
                .verifyComplete();

        // Assert
        verify(paperTrackingsDAO, times(1)).updateItem(any(), any());
    }

    @Test
    void validateSequenceValidFlowABCAllWithDocuments() {
        //Arrange
        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setPaperStatus(new PaperStatus());
        paperTrackings.setValidationFlow(new ValidationFlow());
        paperTrackings.setEvents(List.of(
                buildEvent("RECRN001A", timestamp, businessTimestamp, "REG123", "", List.of(DocumentTypeEnum.AR.getValue())),
                buildEvent("RECRN001B", timestamp, businessTimestamp.plusSeconds(1), "REG123", "", List.of(DocumentTypeEnum.AR.getValue())),
                buildEvent("RECRN001C", timestamp, businessTimestamp.plusSeconds(2), "REG123", "", List.of(DocumentTypeEnum.AR.getValue()))
        ));

        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(sequenceValidator.validateSequence(paperTrackings))
                .verifyComplete();

        // Assert
        verify(paperTrackingsDAO, times(1)).updateItem(any(), any());
    }

    @Test
    void validateSequenceValidFlowDEF() {
        // Arrange
        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setPaperStatus(new PaperStatus());
        paperTrackings.setValidationFlow(new ValidationFlow());
        paperTrackings.setEvents(List.of(
                buildEvent("RECRN002D", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRN002E", timestamp, businessTimestamp.plusSeconds(1), "REG123", "M01", List.of(DocumentTypeEnum.AR.getValue(), DocumentTypeEnum.INDAGINE.getValue())),
                buildEvent("RECRN002F", timestamp, businessTimestamp.plusSeconds(2), "REG123", "", null)
        ));
        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(sequenceValidator.validateSequence(paperTrackings))
                .verifyComplete();

        // Assert
        verify(paperTrackingsDAO, times(1)).updateItem(any(), any());
    }

    @Test
    void validateSequenceValidFlowDEFMultiDocumentFailure() {
        // Arrange
        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();

        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setValidationFlow(new ValidationFlow());
        paperTrackings.setPaperStatus(new PaperStatus());

        paperTrackings.setValidationFlow(new ValidationFlow());
        paperTrackings.setEvents(List.of(
                buildEvent("RECRN002D", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRN002E", timestamp, businessTimestamp.plusSeconds(1), "REG123", "M01", List.of(DocumentTypeEnum.AR.getValue())),
                buildEvent("RECRN002F", timestamp, businessTimestamp.plusSeconds(2), "REG123", "", null)
        ));

        // Act & Assert
        StepVerifier.create(sequenceValidator.validateSequence(paperTrackings))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException &&
                        throwable.getMessage().contains("Attachments are not valid for the sequence"))
                .verify();
    }

    @Test
    void validateSequenceValidFlowDEFMultiDocumentDuplicateFailure() {
        // Arrange
        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();

        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setValidationFlow(new ValidationFlow());
        paperTrackings.setPaperStatus(new PaperStatus());

        paperTrackings.setValidationFlow(new ValidationFlow());
        paperTrackings.setEvents(List.of(
                buildEvent("RECRN002D", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRN002E", timestamp, businessTimestamp.plusSeconds(1), "REG123", "M01", List.of(DocumentTypeEnum.AR.getValue(), DocumentTypeEnum.AR.getValue())),
                buildEvent("RECRN002F", timestamp, businessTimestamp.plusSeconds(2), "REG123", "", null)
        ));

        // Act & Assert
        StepVerifier.create(sequenceValidator.validateSequence(paperTrackings))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException &&
                        throwable.getMessage().contains("Attachments are not valid for the sequence"))
                .verify();
    }

    @Test
    void validateSequenceInvalidEventCount() {
        // Arrange
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setValidationFlow(new ValidationFlow());
        paperTrackings.setPaperStatus(new PaperStatus());

        paperTrackings.setEvents(List.of(
                buildEvent("RECRN001A", Instant.now(), Instant.now(), "REG123", "", null)
        ));

        // Act & Assert
        StepVerifier.create(sequenceValidator.validateSequence(paperTrackings))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException &&
                        throwable.getMessage().contains("Invalid lastEvent for sequence validation"))
                .verify();
    }

    @Test
    void invalidBusinessTimestamp() {
        // Arrange
        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();

        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setValidationFlow(new ValidationFlow());
        paperTrackings.setPaperStatus(new PaperStatus());

        paperTrackings.setValidationFlow(new ValidationFlow());
        paperTrackings.setEvents(List.of(
                buildEvent("RECRN002A", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRN002B", timestamp, businessTimestamp.plusSeconds(1), "REG123", "TESTERR", List.of(DocumentTypeEnum.PLICO.getValue())),
                buildEvent("RECRN002B", timestamp.plus(1, ChronoUnit.DAYS), businessTimestamp.plusSeconds(2), "REG123", "TESTERR", List.of(DocumentTypeEnum.PLICO.getValue())),
                buildEvent("RECRN002C", timestamp, businessTimestamp.plusSeconds(3), "REG123", "", null)
        ));

        // Act & Assert
        StepVerifier.create(sequenceValidator.validateSequence(paperTrackings))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException &&
                        throwable.getMessage().contains("Invalid business timestamps"))
                .verify();
    }

    @Test
    void validateSequenceInvalidSequenceSize() {
        // Arrange
        Instant timestamp = Instant.now();
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setValidationFlow(new ValidationFlow());
        paperTrackings.setPaperStatus(new PaperStatus());

        paperTrackings.setEvents(List.of(
                buildEvent("RECRN001A", timestamp, Instant.now(), "REG123", "", null),
                buildEvent("RECRN001B", timestamp, Instant.now(), "REG123", "", null),
                buildEvent("RECRN002C", timestamp, Instant.now(), "REG123", "", null)
        ));

        // Act & Assert
        StepVerifier.create(sequenceValidator.validateSequence(paperTrackings))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException &&
                        throwable.getMessage().contains("Necessary status code not found in events"))
                .verify();
    }

    @Test
    void validateSequenceInvalidSequenceLetters() {
        // Arrange
        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();

        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setValidationFlow(new ValidationFlow());
        paperTrackings.setPaperStatus(new PaperStatus());

        paperTrackings.setEvents(List.of(
                buildEvent("RECRN001A", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRN001B", timestamp, businessTimestamp.plusSeconds(1), "REG123", "", null),
                buildEvent("RECRN002F", timestamp, businessTimestamp.plusSeconds(2), "REG123", "", null)
        ));

        // Act & Assert
        StepVerifier.create(sequenceValidator.validateSequence(paperTrackings))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException &&
                        throwable.getMessage().contains("Necessary status code not found in events"))
                .verify();
    }

    @Test
    void validateSequenceInvalidSequenceCodes() {
        // Arrange
        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();

        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setValidationFlow(new ValidationFlow());
        paperTrackings.setPaperStatus(new PaperStatus());

        paperTrackings.setEvents(List.of(
                buildEvent("RECRN001A", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRN001B", timestamp, businessTimestamp.plusSeconds(1), "REG123", "", null),
                buildEvent("RECRN002C", timestamp, businessTimestamp.plusSeconds(2), "REG123", "", null)
        ));

        // Act & Assert
        StepVerifier.create(sequenceValidator.validateSequence(paperTrackings))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException &&
                        throwable.getMessage().contains("Necessary status code not found in events"))
                .verify();
    }

    @Test
    void validateSequenceInvalidTimestamps() {
        // Arrange
        PaperTrackings paperTrackings = new PaperTrackings();
        Instant businessTimestamp = Instant.now();

        paperTrackings.setValidationFlow(new ValidationFlow());
        paperTrackings.setPaperStatus(new PaperStatus());

        paperTrackings.setEvents(List.of(
                buildEvent("RECRN001A", Instant.now(), businessTimestamp, "REG123", "", null),
                buildEvent("RECRN001B", Instant.now().plusSeconds(10), businessTimestamp.plusSeconds(1), "REG123", "", List.of(DocumentTypeEnum.AR.getValue())),
                buildEvent("RECRN001C", Instant.now(), businessTimestamp.plusSeconds(2), "REG123", "", null)
        ));

        // Act & Assert
        StepVerifier.create(sequenceValidator.validateSequence(paperTrackings))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException &&
                        throwable.getMessage().contains("Invalid business timestamps"))
                .verify();
    }

    @Test
    void validateSequenceInvalidRegisteredLetterCode() {
        // Arrange
        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();

        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setValidationFlow(new ValidationFlow());
        paperTrackings.setPaperStatus(new PaperStatus());

        paperTrackings.setEvents(List.of(
                buildEvent("RECRN001A", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRN001B", timestamp, businessTimestamp.plusSeconds(1), "REG444", "", List.of(DocumentTypeEnum.AR.getValue())),
                buildEvent("RECRN001C", timestamp, businessTimestamp.plusSeconds(2), "REG123", "", null)
        ));

        // Act & Assert
        StepVerifier.create(sequenceValidator.validateSequence(paperTrackings))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException &&
                        throwable.getMessage().contains("Registered letter codes do not match in sequence"))
                .verify();
    }

    @Test
    void validateSequenceInvalidNullDeliveryFailureCause() {
        // Arrange
        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();

        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setValidationFlow(new ValidationFlow());
        paperTrackings.setPaperStatus(new PaperStatus());

        paperTrackings.setValidationFlow(new ValidationFlow());
        paperTrackings.setEvents(List.of(
                buildEvent("RECRN002A", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRN002B", timestamp, businessTimestamp.plusSeconds(1), "REG123", "", List.of(DocumentTypeEnum.PLICO.getValue())),
                buildEvent("RECRN002C", timestamp, businessTimestamp.plusSeconds(2), "REG123", "", null)
        ));

        // Act & Assert
        StepVerifier.create(sequenceValidator.validateSequence(paperTrackings))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException &&
                        throwable.getMessage().contains("Invalid deliveryFailureCause: "))
                .verify();
    }

    @Test
    void validateSequenceInvalidDeliveryFailureCause() {
        // Arrange
        Instant timestamp = Instant.now();
        Instant businessTimestamp = Instant.now();

        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setValidationFlow(new ValidationFlow());
        paperTrackings.setPaperStatus(new PaperStatus());

        paperTrackings.setValidationFlow(new ValidationFlow());
        paperTrackings.setEvents(List.of(
                buildEvent("RECRN002A", timestamp, businessTimestamp, "REG123", "", null),
                buildEvent("RECRN002B", timestamp, businessTimestamp.plusSeconds(1), "REG123", "TESTERR", List.of(DocumentTypeEnum.PLICO.getValue())),
                buildEvent("RECRN002C", timestamp, businessTimestamp.plusSeconds(2), "REG123", "", null)
        ));

        // Act & Assert
        StepVerifier.create(sequenceValidator.validateSequence(paperTrackings))
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