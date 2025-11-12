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

import static it.pagopa.pn.papertracker.model.EventStatusCodeEnum.RECAG012;

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
    public Mono<Void> handleInternalException(final PnPaperTrackerValidationException ex, Long messageReceiveCount) {
        return handleError(ex.getError(), messageReceiveCount, ex);
    }

    public Mono<Void> handleRetryError(PaperTrackingsErrors paperTrackingsErrors) {
        return handleError(paperTrackingsErrors, null, null);
    }

    public Mono<Void> handleError(PaperTrackingsErrors paperTrackingsErrors, Long messageReceiveCount, PnPaperTrackerValidationException ex) {
        if(ErrorCategory.STATUS_CODE_ERROR.equals(paperTrackingsErrors.getErrorCategory()) && messageReceiveCount < 5){
            return Mono.error(ex);
        }else if(ErrorCategory.STATUS_CODE_ERROR.equals(paperTrackingsErrors.getErrorCategory())){
            log.error("Max retries reached for status code error, inserting error and updating PaperTrackings state to KO for trackingId: {}", paperTrackingsErrors.getTrackingId());
            return insertErrorAndUpdateTrackingsEntity(paperTrackingsErrors).then(Mono.error(ex));
        }
        return insertErrorAndUpdateTrackingsEntity(paperTrackingsErrors);
    }

    private Mono<Void> insertErrorAndUpdateTrackingsEntity(PaperTrackingsErrors paperTrackingsErrors){
        return paperTrackerErrorService.insertPaperTrackingsErrors(paperTrackingsErrors)
                .filter(errors -> paperTrackingsErrors.getType().equals(ErrorType.ERROR))
                .doOnDiscard(PaperTrackingsErrors.class, errors -> log.info("Skipped updating PaperTrackings entity for error with type Warning"))
                .map(unused -> {
                    PaperTrackings paperTrackingsToUpdate = new PaperTrackings();
                    boolean isRECAG012 = RECAG012.name().equalsIgnoreCase(paperTrackingsErrors.getEventThrow());
                    setNewStatus(paperTrackingsToUpdate, isRECAG012);
                    setDematValidationTimestampIfNeeded(paperTrackingsErrors, paperTrackingsToUpdate, isRECAG012);
                    return paperTrackingsToUpdate;
                })
                .flatMap(paperTrackingsToUpdate -> paperTrackerTrackingService.updatePaperTrackingsStatus(paperTrackingsErrors.getTrackingId(), paperTrackingsToUpdate))
                .doOnError(throwable -> log.error("Error inserting entity into PaperTrackingsErrors: {}", throwable.getMessage(), throwable));
    }

    private static void setDematValidationTimestampIfNeeded(PaperTrackingsErrors paperTrackingsErrors, PaperTrackings paperTrackingsToUpdate, boolean isRECAG012) {
        if (Objects.nonNull(paperTrackingsErrors.getDetails().getCause()) && paperTrackingsErrors.getDetails().getCause().equals(ErrorCause.OCR_KO)) {
            ValidationFlow validationFlow = new ValidationFlow();
            if (isRECAG012) {
                validationFlow.setRefinementDematValidationTimestamp(Instant.now());
            } else {
                validationFlow.setFinalEventDematValidationTimestamp(Instant.now());
            }
            paperTrackingsToUpdate.setValidationFlow(validationFlow);
        }
    }

    private static void setNewStatus(PaperTrackings paperTrackingsToUpdate, boolean isRECAG012) {
        if(isRECAG012){
            paperTrackingsToUpdate.setState(PaperTrackingsState.KO);
        } else{
            paperTrackingsToUpdate.setBusinessState(BusinessState.KO);
        }
    }

}
