package it.pagopa.pn.papertracker.it.AR;

import it.pagopa.pn.papertracker.BaseTest;
import it.pagopa.pn.papertracker.it.SequenceLoader;
import it.pagopa.pn.papertracker.it.model.ProductTestCase;
import it.pagopa.pn.papertracker.middleware.msclient.PaperChannelClient;
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
public abstract class AbstractARTestIT extends BaseTest.WithLocalStack {

    @MockitoBean
    protected PaperChannelClient paperChannelClient;

    protected void mockPcRetry(ProductTestCase scenario) {
        getPcRetryResponse(scenario);
        switch (scenario.getName().toUpperCase()) {
            case "OK_RETRY_AR", "OK_RETRY_AR_2", "OK_CAUSA_FORZA_MAGGIORE_AR", "OK_NON_RENDICONTABILE_AR" -> Mockito.when(paperChannelClient.getPcRetry(any(), any())).thenReturn(Mono.just(scenario.getFirstPcRetryResponse()));
            case "FAIL_CON996_PC_RETRY_FURTO_AR" -> Mockito.when(paperChannelClient.getPcRetry(any(), any()))
                    .thenReturn(Mono.just(scenario.getFirstPcRetryResponse()))
                    .thenReturn(Mono.just(scenario.getSecondPcRetryResponse()));
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
