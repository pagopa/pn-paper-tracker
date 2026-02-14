package it.pagopa.pn.papertracker.it.RIR;

import it.pagopa.pn.papertracker.BaseTest;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.SingleStatusUpdate;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PcRetryResponse;
import it.pagopa.pn.papertracker.it.model.ProductTestCase;
import it.pagopa.pn.papertracker.it.SequenceLoader;
import it.pagopa.pn.papertracker.it.SequenceRunner;
import it.pagopa.pn.papertracker.middleware.msclient.PaperChannelClient;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Objects;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@TestPropertySource(locations = "classpath:application.test-ocr-dry.properties")
public class DryRIRTestIT extends BaseTest.WithLocalStack {

    @Autowired
    private SequenceRunner scenarioRunner;

    @MockitoBean
    private PaperChannelClient paperChannelClient;

    @ParameterizedTest(name = "{0}")
    @MethodSource("loadScenarios")
    void runScenario(String fileName, ProductTestCase scenario) throws InterruptedException {
        try {
            checkPcRetry(scenario);
            scenarioRunner.run(scenario);
        }catch (PnPaperTrackerValidationException e){
            //se all'arrivo dell'evento C non sono presenti tutti gli statusCode necessari viene fatta salire l'eccezione
            //per consentire il riaccodamento del messaggio e il successivo reprocess degli eventi,
            // in questo modo si simula il comportamento del sistema in caso di eventi arrivati in ordine non corretto
            if(!e.getError().getDetails().getMessage().equalsIgnoreCase("Necessary status code not found in events: [RECRI003B]")){
                throw e;
            }
        }
    }

    private void checkPcRetry(ProductTestCase scenario) {
        PcRetryResponse response = new PcRetryResponse();
        response.setRetryFound(true);
        response.setPcRetry("PCRETRY_1");

        PcRetryResponse response2 = new PcRetryResponse();
        response2.setRetryFound(true);
        response2.setPcRetry("PCRETRY_2");

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

        switch (scenario.getName().toUpperCase()) {
            case "OK_RETRY_RIR", "OK_PCRETRY_CON996_RIR" -> Mockito.when(paperChannelClient.getPcRetry(any(), any())).thenReturn(Mono.just(response));
            case "FAIL_CON996_PC_RETRY_FURTO_RIR" -> Mockito.when(paperChannelClient.getPcRetry(any(), any()))
                    .thenReturn(Mono.just(response))
                    .thenReturn(Mono.just(response2));
        }
    }

    Stream<Arguments> loadScenarios() throws Exception {
        URI uri = Objects.requireNonNull(Thread.currentThread()
                .getContextClassLoader()
                .getResource("testcase/RIR")).toURI();
       return SequenceLoader.loadScenarios(uri);
    }
}
