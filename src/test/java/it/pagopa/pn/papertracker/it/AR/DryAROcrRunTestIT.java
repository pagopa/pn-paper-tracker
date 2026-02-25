package it.pagopa.pn.papertracker.it.AR;

import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.it.SequenceRunner;
import it.pagopa.pn.papertracker.it.model.ProductTestCase;
import it.pagopa.pn.papertracker.middleware.queue.model.OcrEvent;
import it.pagopa.pn.papertracker.model.OcrStatusEnum;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.stream.Stream;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@TestPropertySource(
        locations = "classpath:application.test-IT.properties",
        properties = {
                "pn.paper-tracker.enable-ocr-validation-for=AR:RUN,890:RUN"
        }
)
public class DryAROcrRunTestIT extends AbstractARTestIT {

    @Autowired
    private SequenceRunner scenarioRunner;

    @ParameterizedTest(name = "{0}")
    @MethodSource("loadTestCases")
    void runScenario(String fileName, ProductTestCase scenario) throws InterruptedException {
        try {
            mockPcRetry(scenario);
            ArgumentCaptor<OcrEvent> ocrEventCaptor = ArgumentCaptor.forClass(OcrEvent.class);
            mockSendToOcr(scenario, ocrEventCaptor);
            scenarioRunner.run(scenario, OcrStatusEnum.RUN, false);
            verifySentToOcr(scenario, ocrEventCaptor);
        }catch (PnPaperTrackerValidationException e){
            //se all'arrivo dell'evento C non sono presenti tutti gli statusCode necessari viene fatta salire l'eccezione
            //per consentire il riaccodamento del messaggio e il successivo reprocess degli eventi,
            // in questo modo si simula il comportamento del sistema in caso di eventi arrivati in ordine non corretto
            if(!e.getError().getDetails().getMessage().equalsIgnoreCase("Necessary status code not found in events: [RECRN001B]")){
                throw e;
            }
        }
    }

    Stream<Arguments> loadTestCases() throws Exception {
        return super.loadTestCases("AR");
    }
}
