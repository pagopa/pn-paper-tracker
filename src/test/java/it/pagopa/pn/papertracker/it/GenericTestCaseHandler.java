package it.pagopa.pn.papertracker.it;

import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingCreationRequest;
import it.pagopa.pn.papertracker.it.model.ProductTestCase;
import it.pagopa.pn.papertracker.model.OcrStatusEnum;

public interface GenericTestCaseHandler {

    String getProductType();

    void beforeInit(ProductTestCase scenario, boolean strictFinalValidation);

    void afterInit(ProductTestCase scenario, TrackingCreationRequest request);

    void sendEvents(ProductTestCase scenario);

    void afterSendEvents(ProductTestCase scenario, OcrStatusEnum ocrStatusEnum, boolean strictValidation);

}