package it.pagopa.pn.papertracker.it._890;

import it.pagopa.pn.papertracker.BaseTest;
import it.pagopa.pn.papertracker.generated.openapi.msclient.pndatavault.model.PaperAddress;
import it.pagopa.pn.papertracker.it.SequenceLoader;
import it.pagopa.pn.papertracker.it.model.ProductTestCase;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.BusinessState;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsState;
import it.pagopa.pn.papertracker.middleware.msclient.DataVaultClient;
import it.pagopa.pn.papertracker.middleware.msclient.PaperChannelClient;
import it.pagopa.pn.papertracker.middleware.msclient.SafeStorageClient;
import it.pagopa.pn.papertracker.middleware.queue.model.OcrEvent;
import it.pagopa.pn.papertracker.middleware.queue.producer.OcrMomProducer;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.Mockito;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Objects;
import java.util.stream.Stream;

import static it.pagopa.pn.papertracker.it.GenericTestCaseHandlerImpl.getPcRetryResponse;
import static org.mockito.ArgumentMatchers.any;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class Abstract890TestIT extends BaseTest.WithLocalStack {

    @MockitoBean
    protected PaperChannelClient paperChannelClient;

    @MockitoBean
    protected DataVaultClient dataVaultClient;

    @MockitoBean
    protected SafeStorageClient safeStorageClient;

    @MockitoBean
    protected OcrMomProducer ocrMomProducer;

    protected void mockPcRetry(ProductTestCase scenario) {
        getPcRetryResponse(scenario);
        switch (scenario.getName().toUpperCase()) {
            case "OK_RETRY_890", "OK_NON_RENDICONTABILE_890" ->
                    Mockito.when(paperChannelClient.getPcRetry(any(), any()))
                            .thenReturn(Mono.just(scenario.getFirstPcRetryResponse()));
        }
    }

    protected void mockDataVault(ProductTestCase scenario) {
        if (scenario.getEvents().stream().anyMatch(testEvent -> Objects.nonNull(testEvent.getAnalogMail().getDiscoveredAddress()))) {
            Mockito.when(dataVaultClient.anonymizeDiscoveredAddress(any(), any())).thenReturn(Mono.just("anonymized_addr_093bcc30-106e-4218-870c-aa9e1d56c4b9"));
            PaperAddress paperAddress = PaperAddress.builder()
                    .name("test")
                    .address("via Roma 10")
                    .cap("00100")
                    .pr("RM")
                    .city("Roma")
                    .build();
            Mockito.when(dataVaultClient.deAnonymizeDiscoveredAddress(any(), any())).thenReturn(Mono.just(paperAddress));
        }
    }

    protected void mockSendToOcr(ProductTestCase scenario) {
        if (scenario.getExpected().getTrackings().stream().anyMatch(paperTrackings -> paperTrackings.getState().equals(PaperTrackingsState.DONE)
                || paperTrackings.getBusinessState().equals(BusinessState.DONE))) {
            Mockito.when(safeStorageClient.getSafeStoragePresignedUrl(any())).thenReturn(Mono.just("Uri"));
            Mockito.doNothing().when(ocrMomProducer).push(any(OcrEvent.class));
        }
    }

    protected void verifySentToOcr(ProductTestCase scenario){
        if (scenario.getExpected().getTrackings().stream().anyMatch(paperTrackings -> paperTrackings.getState().equals(PaperTrackingsState.DONE)
                || paperTrackings.getBusinessState().equals(BusinessState.DONE))) {
            Mockito.verify(ocrMomProducer, Mockito.times(scenario.getExpected().getSentToOcr())).push(any(OcrEvent.class));
        }
    }

    protected Stream<Arguments> loadTestCases(String folder) throws Exception {
        URI uri = Objects.requireNonNull(Thread.currentThread()
                        .getContextClassLoader()
                        .getResource("testcase/" + folder))
                .toURI();
        return SequenceLoader.loadScenarios(uri);
    }
}
