package it.pagopa.pn.papertracker.exception;

import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.service.PaperTrackerErrorService;
import it.pagopa.pn.papertracker.service.PaperTrackerTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaperTrackerExceptionHandler {

    private final PaperTrackerErrorService paperTrackerErrorService;
    private final PaperTrackerTrackingService paperTrackerTrackingService;

    /**
     * Intercetta le eccezioni di tipo {@link PnPaperTrackerValidationException}.
     * <p>
     * Per ogni eccezione intercettata, effettua una putItem sulla tabella PaperTrackingsError
     */
    public Mono<Void> handleInternalException(final PnPaperTrackerValidationException ex) {
        return paperTrackerErrorService.insertPaperTrackingsErrors(ex.getError())
                .filter(paperTrackingsErrors -> ex.getError().getType().equals(ErrorType.ERROR))
                .doOnDiscard(PaperTrackingsErrors.class, paperTrackingsErrors -> log.info("Skipped updating PaperTrackings entity for error with type Warning"))
                .map(unused -> {
                    PaperTrackings paperTrackingsToUpdate = new PaperTrackings();
                    paperTrackingsToUpdate.setState(PaperTrackingsState.KO);
                    if (Objects.nonNull(ex.getError().getDetails().getCause()) && ex.getError().getDetails().getCause().equals(ErrorCause.OCR_KO)) {
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
