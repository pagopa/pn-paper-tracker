package it.pagopa.pn.papertracker.service.handler_step.generic;

import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsState;
import it.pagopa.pn.papertracker.model.HandlerContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckTrackingStateTest {

    @Mock
    private HandlerContext context;

    @Mock
    private PaperProgressStatusEvent paperProgressStatusEvent;

    @Mock
    private PaperTrackings paperTrackings;

    @InjectMocks
    private CheckTrackingState checkTrackingState;

    @Test
    void execute_shouldCompleteWhenStatusCodeStartsWithCON() {
        // Arrange
        when(context.getPaperProgressStatusEvent()).thenReturn(paperProgressStatusEvent);
        when(paperProgressStatusEvent.getStatusCode()).thenReturn("CON998");
        when(context.getPaperTrackings()).thenReturn(paperTrackings);

        // Act & Assert
        StepVerifier.create(checkTrackingState.execute(context))
                .verifyComplete();
    }

    @Test
    void execute_shouldThrowExceptionWhenStateIsDone() {
        // Arrange
        when(context.getPaperProgressStatusEvent()).thenReturn(paperProgressStatusEvent);
        when(paperProgressStatusEvent.getStatusCode()).thenReturn("RECRN002A");
        when(context.getPaperTrackings()).thenReturn(paperTrackings);
        when(paperTrackings.getState()).thenReturn(PaperTrackingsState.DONE);
        when(context.getTrackingId()).thenReturn("tracking-id");

        // Act & Assert
        StepVerifier.create(checkTrackingState.execute(context))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException &&
                        throwable.getMessage().contains("Tracking in state DONE"))
                .verify();
    }

    @Test
    void execute_shouldThrowExceptionWhenStateIsAwaitingOcr() {
        // Arrange
        when(context.getPaperProgressStatusEvent()).thenReturn(paperProgressStatusEvent);
        when(paperProgressStatusEvent.getStatusCode()).thenReturn("RECRN004B");
        when(context.getPaperTrackings()).thenReturn(paperTrackings);
        when(paperTrackings.getState()).thenReturn(PaperTrackingsState.AWAITING_OCR);
        when(context.getTrackingId()).thenReturn("tracking-id");

        // Act & Assert
        StepVerifier.create(checkTrackingState.execute(context))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException &&
                        throwable.getMessage().contains("Tracking in state AWAITING_OCR"))
                .verify();
    }

    @Test
    void execute_shouldCompleteWhenStateIsNotDoneOrAwaitingOcr() {
        // Arrange
        when(context.getPaperProgressStatusEvent()).thenReturn(paperProgressStatusEvent);
        when(paperProgressStatusEvent.getStatusCode()).thenReturn("RECRN006");
        when(context.getPaperTrackings()).thenReturn(paperTrackings);
        when(paperTrackings.getState()).thenReturn(PaperTrackingsState.AWAITING_FINAL_STATUS_CODE);

        // Act & Assert
        StepVerifier.create(checkTrackingState.execute(context))
                .verifyComplete();
    }
}