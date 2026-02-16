package it.pagopa.pn.papertracker.it;

import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.SingleStatusUpdate;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PcRetryResponse;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingCreationRequest;
import it.pagopa.pn.papertracker.it.model.ProductTestCase;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackerDryRunOutputsDAO;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsErrorsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackerDryRunOutputs;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsErrors;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.middleware.queue.consumer.internal.ExternalChannelHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;

import static java.util.stream.Collectors.groupingBy;


@Component
@RequiredArgsConstructor
public class GenericTestCaseHandlerImpl implements GenericTestCaseHandler {

    private final ExternalChannelHandler externalChannelHandler;
    private final PaperTrackingsErrorsDAO paperTrackingsErrorsDAO;
    private final PaperTrackerDryRunOutputsDAO paperTrackerDryRunOutputsDao;
    private final PaperTrackingsDAO paperTrackingsDAO;

    @Override
    public String getProductType() {
        return ProductType.UNKNOWN.getValue();
    }

    @Override
    public void beforeInit(ProductTestCase scenario) {

    }

    @Override
    public void afterInit(ProductTestCase scenario, TrackingCreationRequest request) {

        String requestId = String.join(".", request.getAttemptId(), request.getPcRetry());
        String retryRequestId = requestId.replace("PCRETRY_0", "PCRETRY_1");
        String secondRetryRequestId = requestId.replace("PCRETRY_0", "PCRETRY_2");
        if (!CollectionUtils.isEmpty(scenario.getEvents())) {
            scenario.getEvents().forEach(event -> {
                assert event.getAnalogMail() != null;
                String original = event.getAnalogMail().getRequestId();
                if (original.contains("{{REQUEST_ID_RETRY}}")) {
                    event.getAnalogMail().setRequestId(retryRequestId);
                } else if (original.contains("{{REQUEST_ID_RETRY_2}}")) {
                    event.getAnalogMail().setRequestId(secondRetryRequestId);
                } else {
                    event.getAnalogMail().setRequestId(requestId);
                }
            });
        }
        if (Objects.nonNull(scenario.getExpected())) {
            scenario.getExpected().getOutputs().forEach(event -> {
                if (event.getTrackingId().contains("{{REQUEST_ID_RETRY}}")) {
                    event.setTrackingId(retryRequestId);
                } else if (event.getTrackingId().contains("{{REQUEST_ID_RETRY_2}}")) {
                    event.setTrackingId(secondRetryRequestId);
                } else {
                    event.setTrackingId(requestId);
                }
            });
            scenario.getExpected().getErrors().forEach(event -> {
                if (event.getTrackingId().contains("{{REQUEST_ID_RETRY}}")) {
                    event.setTrackingId(retryRequestId);
                } else if (event.getTrackingId().contains("{{REQUEST_ID_RETRY_2}}")) {
                    event.setTrackingId(secondRetryRequestId);
                } else {
                    event.setTrackingId(requestId);
                }
            });
            scenario.getExpected().getTrackings().forEach(paperTrackings -> {
                if (paperTrackings.getTrackingId().contains("{{REQUEST_ID_RETRY}}")) {
                    paperTrackings.setTrackingId(retryRequestId);
                    if (Objects.nonNull(paperTrackings.getNextRequestIdPcretry())) {
                        paperTrackings.setNextRequestIdPcretry(secondRetryRequestId);
                    }
                } else if (paperTrackings.getTrackingId().contains("{{REQUEST_ID_RETRY_2}}")) {
                    paperTrackings.setTrackingId(secondRetryRequestId);
                } else {
                    paperTrackings.setTrackingId(requestId);
                    if (Objects.nonNull(paperTrackings.getNextRequestIdPcretry())) {
                        paperTrackings.setNextRequestIdPcretry(retryRequestId);
                    }
                }
            });
        }

        resolvePcRetryResponse(scenario);
    }

    private static void resolvePcRetryResponse(ProductTestCase scenario) {
        if (!CollectionUtils.isEmpty(scenario.getEvents())) {
            scenario.getEvents().stream()
                    .map(SingleStatusUpdate::getAnalogMail)
                    .filter(Objects::nonNull)
                    .filter(item -> item.getRequestId().endsWith("PCRETRY_1"))
                    .findFirst()
                    .ifPresent(item -> scenario.getFirstPcRetryResponse().setRequestId(item.getRequestId()));

            scenario.getEvents().stream()
                    .map(SingleStatusUpdate::getAnalogMail)
                    .filter(Objects::nonNull)
                    .filter(item -> item.getRequestId().endsWith("PCRETRY_2"))
                    .findFirst()
                    .ifPresent(item -> scenario.getSecondPcRetryResponse().setRequestId(item.getRequestId()));
        }
    }

    @Override
    public void sendEvents(ProductTestCase scenario) {
        if (!CollectionUtils.isEmpty(scenario.getEvents())) {
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
    }

    @Override
    public void afterSendEvents(ProductTestCase scenario) {
        if (!CollectionUtils.isEmpty(scenario.getEvents())) {
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

            List<PaperTrackings> trackings = paperTrackingsDAO.retrieveAllByTrackingIds(requestIds.stream().toList()).collectList().block();
            ExpectedValidator.verifyTrackingEntities(scenario, trackings);
        }
    }

    public static void getPcRetryResponse(ProductTestCase scenario) {
        PcRetryResponse response = new PcRetryResponse();
        response.setRetryFound(true);
        response.setPcRetry("PCRETRY_1");

        PcRetryResponse response2 = new PcRetryResponse();
        response2.setRetryFound(true);
        response2.setPcRetry("PCRETRY_2");

        if (!CollectionUtils.isEmpty(scenario.getEvents())) {

            scenario.getEvents().stream()
                    .map(SingleStatusUpdate::getAnalogMail)
                    .filter(Objects::nonNull)
                    .filter(item -> item.getRequestId().endsWith("PCRETRY_1"))
                    .findFirst()
                    .ifPresent(item -> {
                        response.setRequestId(item.getRequestId());
                    });

            scenario.getEvents().stream()
                    .map(SingleStatusUpdate::getAnalogMail)
                    .filter(Objects::nonNull)
                    .filter(item -> item.getRequestId().endsWith("PCRETRY_2"))
                    .findFirst()
                    .ifPresent(item -> {
                        response2.setRequestId(item.getRequestId());
                    });
        }

        scenario.setFirstPcRetryResponse(response);
        scenario.setSecondPcRetryResponse(response2);
    }
}
