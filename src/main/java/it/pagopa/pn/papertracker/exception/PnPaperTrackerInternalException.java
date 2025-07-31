package it.pagopa.pn.papertracker.exception;

import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;

import java.time.Instant;
import java.util.List;

public class PnPaperTrackerInternalException extends RuntimeException {

    public PnPaperTrackerInternalException(String message, List<Event> events, String requestId, ProductType productType) {
        super(message);
        PaperTrackingsErrors error = buildPaperTrackingsError(events, requestId, productType);
    }

    private PaperTrackingsErrors buildPaperTrackingsError(List<Event> events, String requestId, ProductType productType) {
        return PaperTrackingsErrors.builder()
                .requestId(requestId)
                .created(Instant.now())
                .errorCategory(ErrorCategory.DATE_ERROR)
                .details(ErrorDetails.builder()
                        .cause(ErrorCategory.DATE_ERROR.name())
                        .message("Invalid sequence or timestamps")
                        .build())
                .flowThrow(FlowThrow.SEQUENCE_VALIDATION)
                .eventThrow(events.toString())
                .productType(productType)
                .build();
    }
}
