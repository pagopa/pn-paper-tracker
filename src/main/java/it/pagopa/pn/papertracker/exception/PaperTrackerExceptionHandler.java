package it.pagopa.pn.papertracker.exception;

import it.pagopa.pn.papertracker.service.PaperTrackerTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class PaperTrackerExceptionHandler {

    private final PaperTrackerTrackingService paperTrackerEventService;

    /**
     * Intercetta le eccezioni di tipo {@link PnPaperTrackerValidationException}.
     * <p>
     * Per ogni eccezione intercettata, effettua una putItem sulla tabella PaperTrackingsError
     */
    @ExceptionHandler(PnPaperTrackerValidationException.class)
    public Mono<Void> handleInternalException(final PnPaperTrackerValidationException ex) {

        return paperTrackerEventService.insertPaperTrackingsErrors(ex.getError())
                .doOnError(throwable -> log.error("Error inserting entity into PaperTrackingsErrors: {}", throwable.getMessage(), throwable));
    }

}
