package it.pagopa.pn.papertracker.it.model;

import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.SingleStatusUpdate;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PcRetryResponse;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingCreationRequest;
import lombok.Data;

import java.util.List;

@Data
public class ProductTestCase {
    private String name;
    private String productType;
    private TrackingCreationRequest initialTracking;
    private List<SingleStatusUpdate> events;
    private Expected expected;
    private PcRetryResponse firstPcRetryResponse;
    private PcRetryResponse secondPcRetryResponse;
}
