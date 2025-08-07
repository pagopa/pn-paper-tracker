package it.pagopa.pn.papertracker.mapper;

import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;

import java.time.Instant;
import java.util.List;

public class PaperTrackingsErrorsMapper {

    public static PaperTrackingsErrors buildPaperTrackingsError(PaperTrackings paperTrackings,
                                                                List<String> statusCodes,
                                                                ErrorCategory errorCategory,
                                                                ErrorCause errorCause,
                                                                String errorMessage,
                                                                FlowThrow flowThrow,
                                                                ErrorType errorType) {
        return PaperTrackingsErrors.builder()
                .trackingId(paperTrackings.getTrackingId())
                .created(Instant.now())
                .errorCategory(errorCategory)
                .details(ErrorDetails.builder()
                        .cause(errorCause)
                        .message(errorMessage)
                        .build())
                .flowThrow(flowThrow)
                .eventThrow(String.join(",", statusCodes))
                .productType(paperTrackings.getProductType())
                .type(errorType)
                .build();
    }
}
