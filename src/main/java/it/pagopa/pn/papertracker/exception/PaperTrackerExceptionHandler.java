package it.pagopa.pn.papertracker.exception;

import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.service.PaperTrackerErrorService;
import it.pagopa.pn.papertracker.service.PaperTrackerTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class PaperTrackerExceptionHandler {

    private final PaperTrackerErrorService paperTrackerErrorService;
    private final PaperTrackerTrackingService paperTrackerTrackingService;



    /**
     * Intercetta le eccezioni di tipo {@link PnPaperTrackerValidationException}.
     * <p>
     * Per ogni eccezione intercettata, effettua una putItem sulla tabella PaperTrackingsError
     */
    @ExceptionHandler(PnPaperTrackerValidationException.class)
    public Mono<Void> handleInternalException(final PnPaperTrackerValidationException ex) {
        return paperTrackerErrorService.insertPaperTrackingsErrors(ex.getError())
                .filter(paperTrackingsErrors -> ex.getError().getType().equals(ErrorType.ERROR))
                .doOnDiscard(PaperTrackingsErrors.class, paperTrackingsErrors -> log.info("Skipped updatying PaperTrackings entity for error with type Warning"))
                .map(unused -> {
                    PaperTrackings paperTrackingsToUpdate = new PaperTrackings();
                    paperTrackingsToUpdate.setTrackingId(ex.getError().getTrackingId());
                    paperTrackingsToUpdate.setState(PaperTrackingsState.KO);
                    if (ex.getError().getDetails().getCause().equals(ErrorCause.OCR_KO)) {
                        ValidationFlow validationFlow = new ValidationFlow();
                        validationFlow.setDematValidationTimestamp(Instant.now());
                        paperTrackingsToUpdate.setValidationFlow(validationFlow);
                    }
                    return paperTrackingsToUpdate;
                })
                .flatMap(paperTrackingsToUpdate -> paperTrackerTrackingService.updatePaperTrackingsStatus(ex.getError().getTrackingId(), paperTrackingsToUpdate))
                .doOnError(throwable -> log.error("Error inserting entity into PaperTrackingsErrors: {}", throwable.getMessage(), throwable));
    }

}
