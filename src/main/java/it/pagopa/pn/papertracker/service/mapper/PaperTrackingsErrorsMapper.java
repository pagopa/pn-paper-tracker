package it.pagopa.pn.papertracker.service.mapper;

import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.ProductType;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingError;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingErrorDetails;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsErrors;

public class PaperTrackingsErrorsMapper {

    private PaperTrackingsErrorsMapper() {
    }

    public static TrackingError toTrackingError(PaperTrackingsErrors paperTrackingsErrors) {
        if (paperTrackingsErrors == null) {
            return null;
        }

        TrackingErrorDetails details = null;
        if (paperTrackingsErrors.getDetails() != null) {
            details = new TrackingErrorDetails()
                    .cause(paperTrackingsErrors.getDetails().getCause() != null ? paperTrackingsErrors.getDetails().getCause().name() : null)
                    .message(paperTrackingsErrors.getDetails().getMessage());
        }

        TrackingError.FlowThrowEnum flowThrowEnum = null;
        if (paperTrackingsErrors.getFlowThrow() != null) {
            flowThrowEnum = TrackingError.FlowThrowEnum.fromValue(paperTrackingsErrors.getFlowThrow().name());
        }

        return new TrackingError()
                .trackingId(paperTrackingsErrors.getTrackingId())
                .created(paperTrackingsErrors.getCreated() != null ? paperTrackingsErrors.getCreated().toString() : null)
                .category(paperTrackingsErrors.getErrorCategory() != null ? paperTrackingsErrors.getErrorCategory().name() : null)
                .details(details)
                .flowThrow(flowThrowEnum)
                .eventThrow(paperTrackingsErrors.getEventThrow())
                .productType(paperTrackingsErrors.getProductType() != null ? ProductType.valueOf(paperTrackingsErrors.getProductType().name()) : null);
    }

}
