package it.pagopa.pn.papertracker.service.handler_step;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerNotFoundException;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PcRetryResponse;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsErrorsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.middleware.msclient.PaperChannelClient;
import it.pagopa.pn.papertracker.model.HandlerContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;
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
    private PaperChannelClient pcRetryApi;

    @InjectMocks
    private RetrySender retrySender;

    @Test
    void execute_retryFound() {
        //ARRANGE
        HandlerContext context = getHandlerContext();
        PcRetryResponse response = getPcRetryResponse(true);

        when(pcRetryApi.getPcRetry(context.getPaperTrackings().getTrackingId())).thenReturn(Mono.just(response));
        when(pnPaperTrackerConfigs.getPaperTrackingsTtlDuration()).thenReturn(Duration.ofDays(3650));
        when(paperTrackingsDAO.putIfAbsent(any())).thenReturn(Mono.just(new PaperTrackings()));

        //ACT
        StepVerifier.create(retrySender.execute(context))
                .verifyComplete();

        verify(paperTrackingsDAO, times(1)).putIfAbsent(any());
        verify(paperTrackingsErrorsDAO, never()).insertError(any());
    }

    @Test
    void execute_retryNotFound() {
        //ARRANGE
        HandlerContext context = getHandlerContext();
        PcRetryResponse response = getPcRetryResponse(false);

        when(pcRetryApi.getPcRetry(context.getPaperTrackings().getTrackingId())).thenReturn(Mono.just(response));

        //ACT
        StepVerifier.create(retrySender.execute(context))
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerValidationException &&
                        throwable.getMessage().contains("Retry not found for trackingId: " + context.getPaperTrackings().getTrackingId()));

        //ASSERT
        verify(paperTrackingsDAO, never()).updateItem(any(), any());
        verify(paperTrackingsDAO, never()).putIfAbsent(any());
    }

    @Test
    void execute_errorFromApi() {
        //ARRANGE
        HandlerContext context = getHandlerContext();
        PnPaperTrackerNotFoundException webClientResponseException = mock(PnPaperTrackerNotFoundException.class);

        when(pcRetryApi.getPcRetry(context.getPaperTrackings().getTrackingId())).thenReturn(Mono.error(webClientResponseException));

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
        context.getPaperTrackings().setTrackingId("requestId");
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
