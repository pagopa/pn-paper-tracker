package it.pagopa.pn.papertracker.mapper;

import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.ProductType;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingError;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingErrorDetails;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

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
        TrackingError trackingError = new TrackingError()
                .trackingId(paperTrackingsErrors.getTrackingId())
                .created(paperTrackingsErrors.getCreated())
                .category(Optional.ofNullable(paperTrackingsErrors.getErrorCategory()).map(ErrorCategory::getValue).orElse(null))
                .eventThrow(paperTrackingsErrors.getEventThrow())
                .flowThrow(Optional.ofNullable(paperTrackingsErrors.getFlowThrow())
                        .map(flowThrow ->  TrackingError.FlowThrowEnum.fromValue(paperTrackingsErrors.getFlowThrow().name()))
                        .orElse(null))
                .productType(Optional.ofNullable(paperTrackingsErrors.getProductType())
                        .map(productType -> ProductType.valueOf(paperTrackingsErrors.getProductType().getValue()))
                        .orElse(null)
                )
                .type(Optional.ofNullable(paperTrackingsErrors.getType())
                        .map(errorType -> TrackingError.TypeEnum.valueOf(errorType.name())).orElse(null));

        if(Objects.nonNull(paperTrackingsErrors.getDetails())){
            trackingError.details(new TrackingErrorDetails()
                    .cause(Optional.ofNullable(paperTrackingsErrors.getDetails().getCause()).map(Enum::name).orElse(null))
                    .message(paperTrackingsErrors.getDetails().getMessage()));
        }

        return trackingError;
    }

}
