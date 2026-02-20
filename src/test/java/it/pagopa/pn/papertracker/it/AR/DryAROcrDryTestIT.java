package it.pagopa.pn.papertracker.it.AR;

import it.pagopa.pn.papertracker.BaseTest;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.it.SequenceLoader;
import it.pagopa.pn.papertracker.it.SequenceRunner;
import it.pagopa.pn.papertracker.it.model.ProductTestCase;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.BusinessState;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsState;
import it.pagopa.pn.papertracker.middleware.msclient.PaperChannelClient;
import it.pagopa.pn.papertracker.middleware.msclient.SafeStorageClient;
import it.pagopa.pn.papertracker.middleware.queue.model.OcrEvent;
import it.pagopa.pn.papertracker.middleware.queue.producer.OcrMomProducer;
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

import static it.pagopa.pn.papertracker.it.GenericTestCaseHandlerImpl.getPcRetryResponse;
import static it.pagopa.pn.papertracker.model.OcrStatusEnum.DRY;
import static org.mockito.ArgumentMatchers.any;
import static reactor.core.publisher.Mono.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@TestPropertySource(locations = "classpath:application.test-ocr-dry.properties")
public class DryAROcrDryTestIT extends AbstractARTestIT {

    @Autowired
    private SequenceRunner scenarioRunner;

    @MockitoBean
    private SafeStorageClient safeStorageClient;

    @MockitoBean
    private OcrMomProducer producer;

    @ParameterizedTest(name = "{0}")
    @MethodSource("loadTestCases")
    void runScenario(String fileName, ProductTestCase scenario) throws InterruptedException {
        try {
            super.mockPcRetry(scenario);
            mockSendToOcr(scenario);
            scenarioRunner.run(scenario, DRY,false);
            Mockito.verify(producer, Mockito.times(scenario.getExpected().getSentToOcr())).push(any(OcrEvent.class));
        }catch (PnPaperTrackerValidationException e){
            //se all'arrivo dell'evento C non sono presenti tutti gli statusCode necessari viene fatta salire l'eccezione
            //per consentire il riaccodamento del messaggio e il successivo reprocess degli eventi,
            // in questo modo si simula il comportamento del sistema in caso di eventi arrivati in ordine non corretto
            if(!e.getError().getDetails().getMessage().equalsIgnoreCase("Necessary status code not found in events: [RECRN001B]")){
                throw e;
            }
        }
    }

    private void mockSendToOcr(ProductTestCase scenario) {
        if(scenario.getExpected().getTrackings().stream().anyMatch(paperTrackings -> paperTrackings.getState().equals(PaperTrackingsState.DONE)
                || paperTrackings.getBusinessState().equals(BusinessState.DONE)) || scenario.getName().equalsIgnoreCase("FAIL_COMPIUTA_GIACENZA_AR")) {
            Mockito.when(safeStorageClient.getSafeStoragePresignedUrl(any())).thenReturn(Mono.just("Uri"));
            Mockito.doNothing().when(producer).push(any(OcrEvent.class));
        }
    }


    Stream<Arguments> loadTestCases() throws Exception {
        return super.loadTestCases("AR");
    }
}
