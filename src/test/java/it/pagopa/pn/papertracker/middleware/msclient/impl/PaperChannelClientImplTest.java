package it.pagopa.pn.papertracker.middleware.msclient.impl;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.api.PcRetryApi;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PcRetryResponse;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.model.HandlerContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaperChannelClientImplTest {

    private static final String TRACKING_ID = "PREPARE_ANALOG_DOMICILE.IUN_XXXX-XXXX-XXXX-202410-X-1.RECINDEX_0.ATTEMPT_0.PCRETRY_1";

    @Mock
    private PcRetryApi pcRetryApi;

    @Mock
    private PnPaperTrackerConfigs config;

    @InjectMocks
    private PaperChannelClientImpl paperChannelClient;

    @Test
    void getPcRetry_dryRun_ocrResponseFlowWithoutPaperProgressStatusEvent_usesFinalStatusCode() {
        // Arrange - contesto del flusso OCR: paperProgressStatusEvent non valorizzato, si usa il finalStatusCode
        HandlerContext context = dryRunContext();
        context.setFinalStatusCode("RECRN002C");
        when(config.getMaxPcRetryMock()).thenReturn(3);

        // Act & Assert
        StepVerifier.create(paperChannelClient.getPcRetry(context, Boolean.FALSE))
                .expectNextMatches(response -> Boolean.TRUE.equals(response.getRetryFound()))
                .verifyComplete();
        verifyNoInteractions(pcRetryApi);
    }

    @Test
    void getPcRetry_dryRun_con996HasNoFurtherRetry() {
        // Arrange
        HandlerContext context = dryRunContext();
        PaperProgressStatusEvent paperProgressStatusEvent = new PaperProgressStatusEvent();
        paperProgressStatusEvent.setStatusCode("CON996");
        context.setPaperProgressStatusEvent(paperProgressStatusEvent);
        when(config.getMaxPcRetryMock()).thenReturn(3);

        // Act & Assert
        StepVerifier.create(paperChannelClient.getPcRetry(context, Boolean.TRUE))
                .expectNextMatches(response -> Boolean.FALSE.equals(response.getRetryFound()))
                .verifyComplete();
        verifyNoInteractions(pcRetryApi);
    }

    @Test
    void getPcRetry_run_callsPaperChannel() {
        // Arrange
        HandlerContext context = dryRunContext();
        context.setDryRunEnabled(false);
        PcRetryResponse response = new PcRetryResponse();
        when(pcRetryApi.getPcRetry(TRACKING_ID, Boolean.FALSE)).thenReturn(Mono.just(response));

        // Act & Assert
        StepVerifier.create(paperChannelClient.getPcRetry(context, Boolean.FALSE))
                .expectNext(response)
                .verifyComplete();
        verify(pcRetryApi).getPcRetry(TRACKING_ID, Boolean.FALSE);
    }

    private HandlerContext dryRunContext() {
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setTrackingId(TRACKING_ID);
        HandlerContext context = new HandlerContext();
        context.setPaperTrackings(paperTrackings);
        context.setDryRunEnabled(true);
        return context;
    }
}
