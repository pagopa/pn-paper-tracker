package it.pagopa.pn.papertracker.it;

import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingCreationRequest;
import it.pagopa.pn.papertracker.it.model.ProductTestCase;

public interface GenericTestCaseHandler {

    String getProductType();

    void beforeInit(ProductTestCase scenario);

    void afterInit(ProductTestCase scenario, TrackingCreationRequest request);

    void sendEvents(ProductTestCase scenario);

    void afterSendEvents(ProductTestCase scenario);

}