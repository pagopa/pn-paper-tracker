package it.pagopa.pn.papertracker.service.handler_step.generic;

import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperStatus;
import it.pagopa.pn.papertracker.middleware.msclient.PaperChannelClient;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PcRetryResponse;
import it.pagopa.pn.papertracker.model.HandlerContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class M10RetryTriggerTest {

    @Mock
    private PaperChannelClient paperChannelClient;

    @Mock
    private PcRetryService pcRetryService;

    @InjectMocks
    private M10RetryTrigger m10RetryTrigger;

    @Test
    void execute_m10FailureCausePresent_executesRetrySender() {
        // Arrange
        HandlerContext context = contextWithFailureCause("M10");
        PcRetryResponse pcRetryResponse = new PcRetryResponse();
        when(paperChannelClient.getPcRetry(context, Boolean.FALSE)).thenReturn(Mono.just(pcRetryResponse));
        when(pcRetryService.handlePcRetryResponse(pcRetryResponse, Boolean.FALSE, context)).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(m10RetryTrigger.execute(context))
                .verifyComplete();

        // Assert
        verify(paperChannelClient).getPcRetry(context, Boolean.FALSE);
        verify(pcRetryService).handlePcRetryResponse(pcRetryResponse, Boolean.FALSE, context);
        verifyNoMoreInteractions(paperChannelClient, pcRetryService);
    }

    @Test
    void execute_m10FailureCauseAbsent_doesNotExecuteRetrySender() {
        // Arrange
        HandlerContext context = contextWithFailureCause("M09");

        // Act
        StepVerifier.create(m10RetryTrigger.execute(context))
                .verifyComplete();

        // Assert
        verifyNoInteractions(paperChannelClient, pcRetryService);
    }

    private HandlerContext contextWithFailureCause(String deliveryFailureCause) {
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setTrackingId("tracking-id");
        PaperStatus paperStatus = new PaperStatus();
        paperStatus.setDeliveryFailureCause(deliveryFailureCause);
        paperTrackings.setPaperStatus(paperStatus);

        HandlerContext context = new HandlerContext();
        context.setTrackingId("tracking-id");
        context.setPaperTrackings(paperTrackings);
        PaperProgressStatusEvent paperProgressStatusEvent = new PaperProgressStatusEvent();
        paperProgressStatusEvent.setStatusCode("RECRN002A");
        context.setPaperProgressStatusEvent(paperProgressStatusEvent);
        return context;
    }
}
