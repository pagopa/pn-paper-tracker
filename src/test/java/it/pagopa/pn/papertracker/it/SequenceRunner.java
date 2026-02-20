package it.pagopa.pn.papertracker.it;

import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingCreationRequest;
import it.pagopa.pn.papertracker.it.model.ProductTestCase;
import it.pagopa.pn.papertracker.middleware.queue.consumer.internal.OcrEventHandler;
import it.pagopa.pn.papertracker.model.OcrStatusEnum;
import it.pagopa.pn.papertracker.service.PaperTrackerTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SequenceRunner {

    private final List<GenericTestCaseHandler> handlers;
    private final PaperTrackerTrackingService paperTrackerTrackingService;
    private final OcrEventHandler ocrEventHandler;

    public void run(ProductTestCase scenario, OcrStatusEnum ocrStatusEnum, boolean strictFinalValidation) throws InterruptedException {
        GenericTestCaseHandler handler = handlers.stream()
                .filter(h -> h.getProductType()
                        .equalsIgnoreCase(scenario.getProductType()))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "No handler for product " + scenario.getProductType())
                );

        String randomIun = UUID.randomUUID().toString();

        scenario.getInitialTracking().setAttemptId(
                scenario.getInitialTracking().getAttemptId()
                        .replace("{{RANDOM_IUN}}", randomIun)
        );

        handler.beforeInit(scenario);

        initPaperTrackings(scenario);

        handler.afterInit(scenario, scenario.getInitialTracking());
        handler.sendEvents(scenario);
        if(Objects.nonNull(scenario.getExpected().getOcrResultPayload()) && ocrStatusEnum.equals(OcrStatusEnum.RUN)){
            receiveOcrResponse(scenario);
        }
        boolean strictValidation = false;
/*        if(scenario.getProductType().equalsIgnoreCase("890")){
            strictValidation = true;
        }*/
       // Thread.sleep(3000); //wait for events to be processed
        handler.afterSendEvents(scenario, ocrStatusEnum, strictFinalValidation);
    }

    private void receiveOcrResponse(ProductTestCase scenario) {
        ocrEventHandler.handleOcrMessage(
                scenario.getExpected().getOcrResultPayload()
        );
    }

    private void initPaperTrackings(ProductTestCase scenario) {
        paperTrackerTrackingService.insertPaperTrackings(scenario.getInitialTracking()).block();
        if(!CollectionUtils.isEmpty(scenario.getEvents())) {

            if (scenario.getEvents().stream().anyMatch(singleStatusUpdate -> {
                assert singleStatusUpdate.getAnalogMail() != null;
                return singleStatusUpdate.getAnalogMail()
                        .getRequestId().equalsIgnoreCase("{{REQUEST_ID_RETRY}}");
            })) {
                paperTrackerTrackingService.insertPaperTrackings(getTrackingCreationRequest(scenario, "PCRETRY_1")).block();
            }

            if (scenario.getEvents().stream().anyMatch(singleStatusUpdate -> {
                assert singleStatusUpdate.getAnalogMail() != null;
                return singleStatusUpdate.getAnalogMail()
                        .getRequestId().equalsIgnoreCase("{{REQUEST_ID_RETRY_2}}");
            })) {
                paperTrackerTrackingService.insertPaperTrackings(getTrackingCreationRequest(scenario, "PCRETRY_2")).block();
            }
        }
    }

    private static TrackingCreationRequest getTrackingCreationRequest(ProductTestCase scenario, String pcRetry) {
        TrackingCreationRequest retryTrackingCreationRequest = new TrackingCreationRequest();
        retryTrackingCreationRequest.setAttemptId(scenario.getInitialTracking().getAttemptId());
        retryTrackingCreationRequest.setProductType(scenario.getInitialTracking().getProductType());
        retryTrackingCreationRequest.setUnifiedDeliveryDriver(scenario.getInitialTracking().getUnifiedDeliveryDriver());
        retryTrackingCreationRequest.setPcRetry(pcRetry);
        return retryTrackingCreationRequest;
    }
}
