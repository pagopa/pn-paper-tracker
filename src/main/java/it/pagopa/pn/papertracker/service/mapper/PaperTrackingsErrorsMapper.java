package it.pagopa.pn.papertracker.service.mapper;

import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.ProductType;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingError;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingErrorDetails;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ErrorCategory;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsErrors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor(access = AccessLevel.NONE)
public class PaperTrackingsErrorsMapper {

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
                );

        if(Objects.nonNull(paperTrackingsErrors.getDetails())){
            trackingError.details(new TrackingErrorDetails()
                    .cause(Optional.ofNullable(paperTrackingsErrors.getDetails().getCause()).map(Enum::name).orElse(null))
                    .message(paperTrackingsErrors.getDetails().getMessage()));
        }

        return trackingError;
    }

}
