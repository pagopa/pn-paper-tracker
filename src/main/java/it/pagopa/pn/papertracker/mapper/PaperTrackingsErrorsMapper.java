package it.pagopa.pn.papertracker.mapper;

import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingError;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.time.Instant;

@RequiredArgsConstructor(access = AccessLevel.NONE)
public class PaperTrackingsErrorsMapper {

    public static PaperTrackingsErrors buildPaperTrackingsError(PaperTrackings paperTrackings,
                                                                String statusCode,
                                                                ErrorCategory errorCategory,
                                                                ErrorCause errorCause,
                                                                String errorMessage,
                                                                FlowThrow flowThrow,
                                                                ErrorType errorType,
                                                                String eventIdThrow) {
        return PaperTrackingsErrors.builder()
                .trackingId(paperTrackings.getTrackingId())
                .created(Instant.now())
                .errorCategory(errorCategory)
                .details(ErrorDetails.builder()
                        .cause(errorCause)
                        .message(errorMessage)
                        .build())
                .flowThrow(flowThrow)
                .eventThrow(statusCode)
                .productType(paperTrackings.getProductType())
                .type(errorType)
                .eventIdThrow(eventIdThrow)
                .build();
    }

    public static TrackingError toTrackingError(PaperTrackingsErrors paperTrackingsErrors) {
        return SmartMapper.mapToClass(paperTrackingsErrors, TrackingError.class);
    }

}
