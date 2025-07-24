package it.pagopa.pn.papertracker.service.mapper;

import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackerCreationRequest;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;

import java.time.Duration;
import java.time.Instant;

public class PaperTrackingsMapper {

    private PaperTrackingsMapper() {
        // Utility class, no instantiation
    }

    public static PaperTrackings toPaperTrackings(TrackerCreationRequest trackerCreationRequest, Duration paperTrackingsTtlDuration) {
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setRequestId(trackerCreationRequest.getRequestId());
        paperTrackings.setUnifiedDeliveryDriver(trackerCreationRequest.getUnifiedDeliveryDriver());
        paperTrackings.setProductType(ProductType.valueOf(trackerCreationRequest.getProductType()));
        paperTrackings.setTtl(Instant.now().plus(paperTrackingsTtlDuration).toEpochMilli());
        return paperTrackings;
    }
}
