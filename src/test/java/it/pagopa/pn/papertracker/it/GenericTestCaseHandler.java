package it.pagopa.pn.papertracker.it;

import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.SingleStatusUpdate;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingCreationRequest;
import it.pagopa.pn.papertracker.it.model.ProductTestCase;
import it.pagopa.pn.papertracker.model.OcrStatusEnum;

import java.util.List;
import java.util.Map;

public interface GenericTestCaseHandler {

    String getProductType();

    void beforeInit(ProductTestCase scenario);

    void afterInit(ProductTestCase scenario, TrackingCreationRequest request);

    void sendEvents(ProductTestCase scenario);

    void afterSendEvents(ProductTestCase scenario, OcrStatusEnum ocrStatusEnum, boolean strictValidation);

}