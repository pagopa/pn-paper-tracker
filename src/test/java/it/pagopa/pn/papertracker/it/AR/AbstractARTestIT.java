package it.pagopa.pn.papertracker.it.AR;

import com.sngular.apigenerator.asyncapi.business_model.model.event.DataDTO;
import com.sngular.apigenerator.asyncapi.business_model.model.event.OcrDataPayloadDTO;
import it.pagopa.pn.papertracker.BaseTest;
import it.pagopa.pn.papertracker.it.SequenceLoader;
import it.pagopa.pn.papertracker.it.model.ProductTestCase;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.BusinessState;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsState;
import it.pagopa.pn.papertracker.middleware.msclient.PaperChannelClient;
import it.pagopa.pn.papertracker.middleware.msclient.SafeStorageClient;
import it.pagopa.pn.papertracker.middleware.queue.model.OcrEvent;
import it.pagopa.pn.papertracker.middleware.queue.producer.OcrMomProducer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static it.pagopa.pn.papertracker.it.GenericTestCaseHandlerImpl.getPcRetryResponse;
import static org.mockito.ArgumentMatchers.any;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractARTestIT extends BaseTest.WithLocalStack {

    private static final Set<String> SINGLE_PC_RETRY_SCENARIOS = Set.of(
            "OK_RETRY_AR",
            "OK_RETRY_AR_2",
            "OK_CAUSA_FORZA_MAGGIORE_AR",
            "OK_NON_RENDICONTABILE_AR"
    );

    private static final Set<String> DOUBLE_PC_RETRY_SCENARIOS = Set.of(
            "FAIL_CON996_PC_RETRY_FURTO_AR"
    );

    private static final Set<String> FORCE_OCR_SCENARIOS = Set.of(
            "FAIL_COMPIUTA_GIACENZA_AR",
            "FAIL_COMPIUTA_GIACENZA_AR_2"
    );

    @MockitoBean
    protected PaperChannelClient paperChannelClient;

    @MockitoBean
    protected SafeStorageClient safeStorageClient;

    @MockitoBean
    protected OcrMomProducer ocrMomProducer;

    protected void mockPcRetry(ProductTestCase scenario) {

        getPcRetryResponse(scenario);
        String scenarioName = scenario.getName().toUpperCase();

        if (SINGLE_PC_RETRY_SCENARIOS.contains(scenarioName)) {
            Mockito.when(paperChannelClient.getPcRetry(any(), any()))
                    .thenReturn(Mono.just(scenario.getFirstPcRetryResponse()));
        }

        if (DOUBLE_PC_RETRY_SCENARIOS.contains(scenarioName)) {
            Mockito.when(paperChannelClient.getPcRetry(any(), any()))
                    .thenReturn(Mono.just(scenario.getFirstPcRetryResponse()))
                    .thenReturn(Mono.just(scenario.getSecondPcRetryResponse()));
        }
    }

    protected Stream<Arguments> loadTestCases(String folder) throws Exception {
        URI uri = Objects.requireNonNull(
                Thread.currentThread()
                        .getContextClassLoader()
                        .getResource("testcase/" + folder)).toURI();

        return SequenceLoader.loadScenarios(uri);
    }

    protected void mockSendToOcr(ProductTestCase scenario,
                                 ArgumentCaptor<OcrEvent> ocrEventCaptor) {

        if (shouldSendToOcr(scenario)) {
            Mockito.when(safeStorageClient.getSafeStoragePresignedUrl(any())).thenReturn(Mono.just("Uri"));
            Mockito.doNothing().when(ocrMomProducer).push(ocrEventCaptor.capture());
        }
    }

    protected void verifySentToOcr(ProductTestCase scenario,
                                   ArgumentCaptor<OcrEvent> ocrEventCaptor) {

/*        if (!shouldVerifyOcr(scenario)) {
            return;
        }

        OcrDataPayloadDTO expectedPayload = scenario.getExpected().getOcrDataPayload();

        Mockito.verify(ocrMomProducer, Mockito.times(scenario.getExpected().getSentToOcr())).push(ocrEventCaptor.capture());

        OcrEvent actualEvent = ocrEventCaptor.getValue();
        Assertions.assertEquals(expectedPayload.getCommandId(), actualEvent.getPayload().getCommandId());
        Assertions.assertEquals(expectedPayload.getCommandType(), actualEvent.getPayload().getCommandType());
        Assertions.assertNotNull(actualEvent.getPayload().getData());

        DataDTO expectedData = expectedPayload.getData();
        DataDTO actualData = actualEvent.getPayload().getData();

        Assertions.assertEquals(expectedData.getDocumentType(), actualData.getDocumentType());
        Assertions.assertEquals(expectedData.getProductType(), actualData.getProductType());
        Assertions.assertEquals(expectedData.getUnifiedDeliveryDriver(), actualData.getUnifiedDeliveryDriver());
        Assertions.assertNotNull(actualData.getDetails());
        Assertions.assertEquals(expectedData.getDetails().getDeliveryDetailCode(), actualData.getDetails().getDeliveryDetailCode());
        Assertions.assertEquals(expectedData.getDetails().getRegisteredLetterCode(), actualData.getDetails().getRegisteredLetterCode());
        Assertions.assertEquals(expectedData.getDetails().getAttachment(), actualData.getDetails().getAttachment());
        Assertions.assertEquals(expectedData.getDetails().getNotificationDate(), actualData.getDetails().getNotificationDate());
        Assertions.assertEquals(expectedData.getDetails().getDeliveryFailureCause(), actualData.getDetails().getDeliveryFailureCause());
        Assertions.assertEquals(expectedData.getDetails().getDeliveryAttemptDate(), actualData.getDetails().getDeliveryAttemptDate());*/
    }

    private boolean shouldSendToOcr(ProductTestCase scenario) {

        boolean doneTracking = scenario.getExpected().getTrackings().stream().anyMatch(t ->
                        t.getState() == PaperTrackingsState.DONE || t.getBusinessState() == BusinessState.DONE);

        boolean forcedScenario = FORCE_OCR_SCENARIOS.contains(scenario.getName());
        boolean isZip = scenario.getName().contains("ZIP");
        return (doneTracking || forcedScenario) && !isZip;
    }

    private boolean shouldVerifyOcr(ProductTestCase scenario) {
        boolean doneTracking = scenario.getExpected().getTrackings().stream().anyMatch(t ->
                        t.getState() == PaperTrackingsState.DONE || t.getBusinessState() == BusinessState.DONE);

        boolean isZip = scenario.getName().contains("ZIP");
        return doneTracking && !isZip;
    }
}