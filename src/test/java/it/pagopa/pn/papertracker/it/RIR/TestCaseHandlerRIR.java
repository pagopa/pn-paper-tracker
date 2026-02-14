package it.pagopa.pn.papertracker.it.RIR;

import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingCreationRequest;
import it.pagopa.pn.papertracker.it.ExpectedValidator;
import it.pagopa.pn.papertracker.it.GenericTestCaseHandler;
import it.pagopa.pn.papertracker.it.model.ProductTestCase;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackerDryRunOutputsDAO;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsErrorsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackerDryRunOutputs;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsErrors;
import it.pagopa.pn.papertracker.middleware.queue.consumer.internal.ExternalChannelHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

import static java.util.stream.Collectors.groupingBy;

@Component
@RequiredArgsConstructor
public class TestCaseHandlerRIR implements GenericTestCaseHandler {

    private final ExternalChannelHandler externalChannelHandler;
    private final PaperTrackingsErrorsDAO paperTrackingsErrorsDAO;
    private final PaperTrackerDryRunOutputsDAO paperTrackerDryRunOutputsDao;

    @Override
    public String getProductType() {
        return "RIR";
    }

    @Override
    public void beforeInit(ProductTestCase scenario) {
    }

    @Override
    public void afterInit(ProductTestCase scenario, TrackingCreationRequest request) {
        String requestId = String.join(".", request.getAttemptId(), request.getPcRetry());
        String retryRequestId = requestId.replace("PCRETRY_0", "PCRETRY_1");
        String secondRetryRequestId = requestId.replace("PCRETRY_0", "PCRETRY_2");
        scenario.getEvents().forEach(event -> {
            assert event.getAnalogMail() != null;
            String original = event.getAnalogMail().getRequestId();
            if (original.contains("{{REQUEST_ID_RETRY}}")) {
                event.getAnalogMail().setRequestId(retryRequestId);
            }else if (original.contains("{{REQUEST_ID_RETRY_2}}")) {
                event.getAnalogMail().setRequestId(secondRetryRequestId);
            }else {
                event.getAnalogMail().setRequestId(requestId);
            }
        });
        scenario.getExpected().getOutputs().forEach(event -> {
            if (event.getTrackingId().contains("{{REQUEST_ID_RETRY}}")) {
                event.setTrackingId(retryRequestId);
            }else if (event.getTrackingId().contains("{{REQUEST_ID_RETRY_2}}")) {
                event.setTrackingId(secondRetryRequestId);
            }else {
                event.setTrackingId(requestId);
            }
        });
        scenario.getExpected().getErrors().forEach(event -> {
            if (event.getTrackingId().contains("{{REQUEST_ID_RETRY}}")) {
                event.setTrackingId(retryRequestId);
            }else if (event.getTrackingId().contains("{{REQUEST_ID_RETRY_2}}")) {
                event.setTrackingId(secondRetryRequestId);
            }else {
                event.setTrackingId(requestId);
            }
        });
    }

    @Override
    public void sendEvents(ProductTestCase scenario) {
        scenario.getEvents().forEach(event ->
                externalChannelHandler.handleExternalChannelMessage(
                        event,
                        true,
                        null,
                        UUID.randomUUID().toString(),
                        null
                )
        );
    }

    @Override
    public void afterSendEvents(ProductTestCase scenario) {
        Set<String> requestIds = scenario.getEvents().stream()
                .collect(groupingBy(event -> {
                    assert event.getAnalogMail() != null;
                    return event.getAnalogMail().getRequestId();
                }))
                .keySet();
        List<PaperTrackingsErrors> errors = new ArrayList<>();
        requestIds.forEach(requestId -> errors.addAll(Objects.requireNonNull(paperTrackingsErrorsDAO.retrieveErrors(requestId).collectList().block())));
        ExpectedValidator.verifyErrors(scenario, errors);

        List<PaperTrackerDryRunOutputs> outputs = new ArrayList<>();
        requestIds.forEach(requestId -> outputs.addAll(Objects.requireNonNull(paperTrackerDryRunOutputsDao.retrieveOutputEvents(requestId).collectList().block())));
        ExpectedValidator.verifyOutputs(scenario, outputs);
    }
}
