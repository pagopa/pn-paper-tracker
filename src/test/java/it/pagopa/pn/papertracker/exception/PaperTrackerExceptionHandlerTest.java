package it.pagopa.pn.papertracker.exception;

import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsErrors;
import it.pagopa.pn.papertracker.service.PaperTrackerEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaperTrackerExceptionHandlerTest {

    @Mock
    private PaperTrackerEventService paperTrackerEventService;

    private PaperTrackerExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new PaperTrackerExceptionHandler(paperTrackerEventService);
    }

    @Test
    void handleInternalExceptionSuccessfully() {
        // ARRANGE
        PnPaperTrackerValidationException exception = new PnPaperTrackerValidationException("Validation error", new PaperTrackingsErrors());
        when(paperTrackerEventService.insertPaperTrackingsErrors(exception.getError())).thenReturn(Mono.empty());

        // ACT
        Mono<Void> response = exceptionHandler.handleInternalException(exception);

        // ASSERT
        StepVerifier.create(response)
                .verifyComplete();
        verify(paperTrackerEventService, times(1)).insertPaperTrackingsErrors(exception.getError());
    }

}