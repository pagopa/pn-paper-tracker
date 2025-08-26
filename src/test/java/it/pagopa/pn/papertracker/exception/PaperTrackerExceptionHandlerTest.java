package it.pagopa.pn.papertracker.exception;

import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsErrors;
import it.pagopa.pn.papertracker.service.PaperTrackerErrorService;
import it.pagopa.pn.papertracker.service.PaperTrackerTrackingService;
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
    private PaperTrackerErrorService paperTrackerErrorService;

    @Mock
    private PaperTrackerTrackingService paperTrackerEventService;

    private PaperTrackerExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new PaperTrackerExceptionHandler(paperTrackerErrorService, paperTrackerEventService);
    }

    @Test
    void handleInternalExceptionSuccessfully() {
        // ARRANGE
        PnPaperTrackerValidationException exception = new PnPaperTrackerValidationException("Validation error", new PaperTrackingsErrors());
        when(paperTrackerErrorService.insertPaperTrackingsErrors(exception.getError())).thenReturn(Mono.empty());

        // ACT
        Mono<Void> response = exceptionHandler.handleInternalException(exception);

        // ASSERT
        StepVerifier.create(response)
                .verifyComplete();
        verify(paperTrackerErrorService, times(1)).insertPaperTrackingsErrors(exception.getError());
    }

}