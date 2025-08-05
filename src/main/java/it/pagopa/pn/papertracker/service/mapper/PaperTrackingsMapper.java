package it.pagopa.pn.papertracker.service.mapper;

import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PcRetryResponse;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingCreationRequest;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsState;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;

import java.time.Duration;
import java.time.Instant;

public class PaperTrackingsMapper {

    private PaperTrackingsMapper() {
        // Utility class, no instantiation
    }

    public static PaperTrackings toPaperTrackings(TrackingCreationRequest trackingCreationRequest, Duration paperTrackingsTtlDuration) {
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setTrackingId(trackingCreationRequest.getTrackingId());
        paperTrackings.setUnifiedDeliveryDriver(trackingCreationRequest.getUnifiedDeliveryDriver());
        paperTrackings.setProductType(ProductType.valueOf(trackingCreationRequest.getProductType()));
        paperTrackings.setState(PaperTrackingsState.AWAITING_FINAL_STATUS_CODE);
        paperTrackings.setTtl(Instant.now().plus(paperTrackingsTtlDuration).toEpochMilli());
        return paperTrackings;
    }

    public static PaperTrackings toPaperTrackings(PcRetryResponse pcRetryResponse, Duration paperTrackingsTtlDuration, ProductType productType){
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setTrackingId(pcRetryResponse.getRequestId());
        paperTrackings.setUnifiedDeliveryDriver(pcRetryResponse.getDeliveryDriverId());
        paperTrackings.setProductType(productType);
        paperTrackings.setState(PaperTrackingsState.AWAITING_FINAL_STATUS_CODE);
        paperTrackings.setTtl(Instant.now().plus(paperTrackingsTtlDuration).toEpochMilli());
        return paperTrackings;
    }
}
