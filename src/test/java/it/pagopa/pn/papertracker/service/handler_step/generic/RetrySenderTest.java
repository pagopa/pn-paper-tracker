package it.pagopa.pn.papertracker.service.handler_step.generic;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerNotFoundException;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PcRetryResponse;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsErrorsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.middleware.msclient.PaperChannelClient;
import it.pagopa.pn.papertracker.model.HandlerContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RetrySenderTest {

    @Mock
    private PaperTrackingsDAO paperTrackingsDAO;

    @Mock
    private PaperTrackingsErrorsDAO paperTrackingsErrorsDAO;

    @Mock
    private PnPaperTrackerConfigs pnPaperTrackerConfigs;

    @Mock
    private PcRetryService pcRetryService;

    @Mock
    private PaperChannelClient pcRetryApi;

    @InjectMocks
    private RetrySender retrySender;

    @Test
    void execute_retryFound() {
        //ARRANGE
        HandlerContext context = getHandlerContext();
        PcRetryResponse response = getPcRetryResponse(true);

        when(pcRetryApi.getPcRetry(any(), any())).thenReturn(Mono.just(response));
        when(pcRetryService.handlePcRetryResponse(any(), anyBoolean(), any())).thenReturn(Mono.empty());
        //ACT
        StepVerifier.create(retrySender.execute(context))
                .verifyComplete();

        //ASSERT
        verify(pcRetryService, times(1)).handlePcRetryResponse(any(), anyBoolean(), any());
    }

    @Test
    void execute_retryNotFound() {
        HandlerContext context = getHandlerContext();
        PcRetryResponse response = getPcRetryResponse(false);

        when(pcRetryApi.getPcRetry(any(), any())).thenReturn(Mono.just(response));
        when(pcRetryService.handlePcRetryResponse(any(), anyBoolean(), any())).thenReturn(Mono.empty());
        //ACT
        StepVerifier.create(retrySender.execute(context))
                .verifyComplete();

        //ASSERT
        verify(pcRetryService, times(1)).handlePcRetryResponse(any(), anyBoolean(), any());
    }

    @Test
    void execute_errorFromApi() {
        //ARRANGE
        HandlerContext context = getHandlerContext();
        PnPaperTrackerNotFoundException webClientResponseException = mock(PnPaperTrackerNotFoundException.class);

        when(pcRetryApi.getPcRetry(any(), any())).thenReturn(Mono.error(webClientResponseException));

        //ACT
        StepVerifier.create(retrySender.execute(context))
                .expectError(PnPaperTrackerNotFoundException.class)
                .verify();

        //ASSERT
        verifyNoInteractions(paperTrackingsDAO);
        verifyNoInteractions(paperTrackingsErrorsDAO);
    }

    @Test
    void execute_genericErrorFromApi_propagatesOriginalErrorWithoutHandlingResponse() {
        //ARRANGE
        HandlerContext context = getHandlerContext();
        RuntimeException serverError = new RuntimeException("paper-channel 500");
        when(pcRetryApi.getPcRetry(any(), any())).thenReturn(Mono.error(serverError));

        //ACT & ASSERT
        StepVerifier.create(retrySender.execute(context))
                .expectErrorMatches(serverError::equals)
                .verify();
        verifyNoInteractions(pcRetryService);
        Assertions.assertNull(context.getNextRequestIdPcRetry());
        Assertions.assertFalse(context.isMaxPcRetryReached());
    }

    @Test
    void execute_errorFromApiOnOcrResponseFlowWithoutPaperProgressStatusEvent_propagatesOriginalError() {
        //ARRANGE
        HandlerContext context = getHandlerContext();
        context.setPaperProgressStatusEvent(null);
        context.setFinalStatusCode("RECRN002C");
        RuntimeException serverError = new RuntimeException("paper-channel timeout");
        when(pcRetryApi.getPcRetry(any(), any())).thenReturn(Mono.error(serverError));

        //ACT & ASSERT
        StepVerifier.create(retrySender.execute(context))
                .expectErrorMatches(serverError::equals)
                .verify();
        verifyNoInteractions(pcRetryService);
    }

    private HandlerContext getHandlerContext() {
        HandlerContext context = new HandlerContext();
        context.setPaperTrackings(new PaperTrackings());
        context.getPaperTrackings().setTrackingId("requestId");
        context.getPaperTrackings().setProductType(ProductType.AR.getValue());
        context.setPaperProgressStatusEvent(new PaperProgressStatusEvent());
        context.getPaperProgressStatusEvent().setRequestId("requestId");
        context.getPaperProgressStatusEvent().setStatusCode("statusCode");
        return context;
    }

    private PcRetryResponse getPcRetryResponse(boolean retryFound) {
        PcRetryResponse response = new PcRetryResponse();
        response.setRequestId("requestId");
        response.setParentRequestId("parentRequestId");
        response.setDeliveryDriverId("deliveryDriverId");
        response.setRetryFound(retryFound);
        return response;
    }
}
