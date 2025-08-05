package it.pagopa.pn.papertracker.service.handler_step;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerNotFoundException;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.api.PcRetryApi;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PcRetryResponse;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsErrorsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.model.HandlerContext;
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
    private PcRetryApi pcRetryApi;

    @InjectMocks
    private RetrySender retrySender;

    @Test
    void execute_retryFound() {
        //ARRANGE
        HandlerContext context = getHandlerContext();
        PcRetryResponse response = getPcRetryResponse(true);

        when(pcRetryApi.getPcRetry(context.getPaperTrackings().getRequestId())).thenReturn(Mono.just(response));
        when(pnPaperTrackerConfigs.getPaperTrackingsTtlDuration()).thenReturn(Duration.ofDays(3650));

        //ACT
        StepVerifier.create(retrySender.execute(context))
                .verifyComplete();

        //ASSERT
        verify(paperTrackingsDAO).updateItem(eq(context.getPaperTrackings().getRequestId()), argThat(paperTrackings ->
                paperTrackings.getNextRequestIdPcretry().equals(response.getRequestId())));
        verify(paperTrackingsDAO).putIfAbsent(
                argThat(paperTrackings -> paperTrackings.getRequestId().equals(response.getRequestId()) &&
                        paperTrackings.getUnifiedDeliveryDriver().equals(response.getDeliveryDriverId()) &&
                        paperTrackings.getProductType() == context.getPaperTrackings().getProductType()));
        verify(paperTrackingsErrorsDAO, never()).insertError(any());
    }

    @Test
    void execute_retryNotFound() {
        //ARRANGE
        HandlerContext context = getHandlerContext();
        PcRetryResponse response = getPcRetryResponse(false);

        when(pcRetryApi.getPcRetry(context.getPaperTrackings().getRequestId())).thenReturn(Mono.just(response));

        //ACT
        StepVerifier.create(retrySender.execute(context))
                .verifyComplete();

        //ASSERT
        verify(paperTrackingsDAO, never()).updateItem(any(), any());
        verify(paperTrackingsDAO, never()).putIfAbsent(any());
        verify(paperTrackingsErrorsDAO).insertError(argThat(error ->
                error.getRequestId().equals(response.getRequestId()) &&
                        error.getDetails().getMessage().isEmpty() &&
                        error.getFlowThrow() == null &&
                        error.getEventThrow().equals(context.getPaperProgressStatusEvent().getStatusCode()) &&
                        error.getProductType() == context.getPaperTrackings().getProductType()));
    }

    @Test
    void execute_errorFromApi() {
        //ARRANGE
        HandlerContext context = getHandlerContext();

        when(pcRetryApi.getPcRetry(context.getPaperTrackings().getRequestId())).thenReturn(Mono.error(new RuntimeException()));

        //ACT
        StepVerifier.create(retrySender.execute(context))
                .expectError(PnPaperTrackerNotFoundException.class)
                .verify();

        //ASSERT
        verifyNoInteractions(paperTrackingsDAO);
        verifyNoInteractions(paperTrackingsErrorsDAO);
    }

    private HandlerContext getHandlerContext() {
        HandlerContext context = new HandlerContext();
        context.setPaperTrackings(new PaperTrackings());
        context.getPaperTrackings().setRequestId("requestId");
        context.getPaperTrackings().setProductType(ProductType.AR);
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
