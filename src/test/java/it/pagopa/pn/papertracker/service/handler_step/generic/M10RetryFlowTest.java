package it.pagopa.pn.papertracker.service.handler_step.generic;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.exception.PaperTrackerExceptionHandler;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PaperChannelUpdate;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PcRetryResponse;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SendEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.StatusCodeEnum;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackerDryRunOutputsDAO;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.middleware.eventBridge.EventBridgePublisher;
import it.pagopa.pn.papertracker.middleware.msclient.PaperChannelClient;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.PaperTrackerErrorService;
import it.pagopa.pn.papertracker.service.PaperTrackerTrackingService;
import it.pagopa.pn.papertracker.service.handler_step.Handler;
import it.pagopa.pn.papertracker.service.handler_step.HandlerImpl;
import it.pagopa.pn.papertracker.utils.LogUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Verifica il comportamento end-to-end degli step CheckTrackingState -> M10RetryTrigger -> OutputTargetSender
 * per la gestione del retry M10, sia nel flusso degli eventi finali (pn-external_channel_to_paper_tracker)
 * sia nel flusso di risposta OCR (dove il paperProgressStatusEvent non è valorizzato).
 */
@ExtendWith(MockitoExtension.class)
class M10RetryFlowTest {

    private static final String TRACKING_ID = "PREPARE_ANALOG_DOMICILE.IUN_XXXX-XXXX-XXXX-202410-X-1.RECINDEX_0.ATTEMPT_0.PCRETRY_0";
    private static final String NEXT_TRACKING_ID = "PREPARE_ANALOG_DOMICILE.IUN_XXXX-XXXX-XXXX-202410-X-1.RECINDEX_0.ATTEMPT_0.PCRETRY_1";
    private static final String FINAL_STATUS_CODE = "RECRN002C";
    private static final String EVENT_ID = "event-id";
    private static final boolean FINAL_EVENT_FLOW = true;
    private static final boolean OCR_RESPONSE_FLOW = false;

    @Mock
    private PaperChannelClient paperChannelClient;
    @Mock
    private PaperTrackerErrorService paperTrackerErrorService;
    @Mock
    private PaperTrackerTrackingService paperTrackerTrackingService;
    @Mock
    private PnPaperTrackerConfigs configs;
    @Mock
    private PaperTrackerDryRunOutputsDAO paperTrackerDryRunOutputsDAO;
    @Mock
    private PaperTrackingsDAO paperTrackingsDAO;
    @Mock
    private EventBridgePublisher eventBridgePublisher;
    @Mock
    private LogUtility logUtility;

    private Handler handler;

    @BeforeEach
    void setUp() {
        PaperTrackerExceptionHandler exceptionHandler = new PaperTrackerExceptionHandler(paperTrackerErrorService, paperTrackerTrackingService);
        PcRetryService pcRetryService = new PcRetryService(exceptionHandler);
        M10RetryTrigger m10RetryTrigger = new M10RetryTrigger(paperChannelClient, pcRetryService);
        OutputTargetSender outputTargetSender = new OutputTargetSender(configs, paperTrackerDryRunOutputsDAO, paperTrackingsDAO, eventBridgePublisher, logUtility);
        handler = new HandlerImpl(List.of(new CheckTrackingState(), m10RetryTrigger, outputTargetSender));
    }

    @ParameterizedTest(name = "finalEventFlow={0}")
    @ValueSource(booleans = {FINAL_EVENT_FLOW, OCR_RESPONSE_FLOW})
    void m10RetryNotFound_sendsProgressToDeliveryPushAndSetsTrackingKo(boolean finalEventFlow) {
        // Arrange
        HandlerContext context = buildContext(finalEventFlow, PaperTrackingsState.AWAITING_REFINEMENT);
        when(paperChannelClient.getPcRetry(context, Boolean.FALSE)).thenReturn(Mono.just(retryNotFound()));
        when(paperTrackerErrorService.insertPaperTrackingsError(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(paperTrackerTrackingService.updatePaperTrackingsStatus(eq(TRACKING_ID), any())).thenReturn(Mono.empty());
        when(eventBridgePublisher.publish(any())).thenReturn(Mono.just(PutEventsResponse.builder().build()));

        // Act
        StepVerifier.create(handler.execute(context)).verifyComplete();

        // Assert - delivery-push riceve l'evento finale come PROGRESS
        SendEvent sent = captureSingleSentEvent();
        assertEquals(StatusCodeEnum.PROGRESS, sent.getStatusCode());
        assertEquals(FINAL_STATUS_CODE, sent.getStatusDetail());

        // Assert - errore MAX_RETRY_REACHED_ERROR inserito
        ArgumentCaptor<PaperTrackingsErrors> errorCaptor = ArgumentCaptor.forClass(PaperTrackingsErrors.class);
        verify(paperTrackerErrorService).insertPaperTrackingsError(errorCaptor.capture());
        PaperTrackingsErrors error = errorCaptor.getValue();
        assertEquals(ErrorCategory.MAX_RETRY_REACHED_ERROR, error.getErrorCategory());
        assertEquals(ErrorType.ERROR, error.getType());
        assertEquals(FlowThrow.RETRY_PHASE, error.getFlowThrow());
        assertEquals(FINAL_STATUS_CODE, error.getEventThrow());
        assertEquals(EVENT_ID, error.getEventIdThrow());

        // Assert - tracking in KO e mai sovrascritto con DONE
        ArgumentCaptor<PaperTrackings> statusCaptor = ArgumentCaptor.forClass(PaperTrackings.class);
        verify(paperTrackerTrackingService).updatePaperTrackingsStatus(eq(TRACKING_ID), statusCaptor.capture());
        assertEquals(PaperTrackingsState.KO, statusCaptor.getValue().getState());
        assertEquals(BusinessState.KO, statusCaptor.getValue().getBusinessState());
        verifyNoInteractions(paperTrackingsDAO);
        assertTrue(context.isMaxPcRetryReached());
    }

    @ParameterizedTest(name = "finalEventFlow={0}")
    @ValueSource(booleans = {FINAL_EVENT_FLOW, OCR_RESPONSE_FLOW})
    void m10RetryFound_sendsProgressToDeliveryPushAndSetsTrackingDoneWithNextRequestId(boolean finalEventFlow) {
        // Arrange
        HandlerContext context = buildContext(finalEventFlow, PaperTrackingsState.AWAITING_REFINEMENT);
        when(paperChannelClient.getPcRetry(context, Boolean.FALSE)).thenReturn(Mono.just(retryFound()));
        when(eventBridgePublisher.publish(any())).thenReturn(Mono.just(PutEventsResponse.builder().build()));
        when(paperTrackingsDAO.updateItem(eq(TRACKING_ID), any())).thenReturn(Mono.just(new PaperTrackings()));

        // Act
        StepVerifier.create(handler.execute(context)).verifyComplete();

        // Assert
        assertEquals(StatusCodeEnum.PROGRESS, captureSingleSentEvent().getStatusCode());
        ArgumentCaptor<PaperTrackings> captor = ArgumentCaptor.forClass(PaperTrackings.class);
        verify(paperTrackingsDAO).updateItem(eq(TRACKING_ID), captor.capture());
        assertEquals(PaperTrackingsState.DONE, captor.getValue().getState());
        assertEquals(NEXT_TRACKING_ID, captor.getValue().getNextRequestIdPcretry());
        verifyNoInteractions(paperTrackerErrorService, paperTrackerTrackingService);
    }

    @ParameterizedTest(name = "finalEventFlow={0}")
    @ValueSource(booleans = {FINAL_EVENT_FLOW, OCR_RESPONSE_FLOW})
    void m10PaperChannelError_propagatesOriginalErrorWithoutSideEffects(boolean finalEventFlow) {
        // Arrange
        HandlerContext context = buildContext(finalEventFlow, PaperTrackingsState.AWAITING_REFINEMENT);
        RuntimeException serverError = new RuntimeException("paper-channel 500");
        when(paperChannelClient.getPcRetry(context, Boolean.FALSE)).thenReturn(Mono.error(serverError));

        // Act & Assert - l'errore originale viene propagato (retry SQS -> DLQ)
        StepVerifier.create(handler.execute(context))
                .expectErrorMatches(serverError::equals)
                .verify();

        // Nessun evento inviato a delivery-push, nessun errore salvato, nessun cambio di stato
        verifyNoInteractions(eventBridgePublisher, paperTrackerDryRunOutputsDAO, paperTrackingsDAO,
                paperTrackerErrorService, paperTrackerTrackingService);
    }

    @Test
    void m10RedriveAfterMaxPcRetryReached_unlocksTrackingWhenRetryBecomesAvailable() {
        // Arrange - tracking già portato in KO da un precedente MAX_RETRY_REACHED_ERROR
        HandlerContext context = buildContext(FINAL_EVENT_FLOW, PaperTrackingsState.KO);
        context.getPaperTrackings().setBusinessState(BusinessState.KO);
        when(paperChannelClient.getPcRetry(context, Boolean.FALSE)).thenReturn(Mono.just(retryFound()));
        when(eventBridgePublisher.publish(any())).thenReturn(Mono.just(PutEventsResponse.builder().build()));
        when(paperTrackingsDAO.updateItem(eq(TRACKING_ID), any())).thenReturn(Mono.just(new PaperTrackings()));

        // Act
        StepVerifier.create(handler.execute(context)).verifyComplete();

        // Assert - CheckTrackingState non blocca il KO e il tracking viene chiuso con il nuovo PCRETRY
        assertEquals(StatusCodeEnum.PROGRESS, captureSingleSentEvent().getStatusCode());
        ArgumentCaptor<PaperTrackings> captor = ArgumentCaptor.forClass(PaperTrackings.class);
        verify(paperTrackingsDAO).updateItem(eq(TRACKING_ID), captor.capture());
        assertEquals(PaperTrackingsState.DONE, captor.getValue().getState());
        assertEquals(BusinessState.DONE, captor.getValue().getBusinessState());
        assertEquals(NEXT_TRACKING_ID, captor.getValue().getNextRequestIdPcretry());
        verifyNoInteractions(paperTrackerErrorService, paperTrackerTrackingService);
    }

    @Test
    void m10RedriveAfterMaxPcRetryReached_stillNoRetry_keepsTrackingKo() {
        // Arrange
        HandlerContext context = buildContext(FINAL_EVENT_FLOW, PaperTrackingsState.KO);
        context.getPaperTrackings().setBusinessState(BusinessState.KO);
        when(paperChannelClient.getPcRetry(context, Boolean.FALSE)).thenReturn(Mono.just(retryNotFound()));
        when(paperTrackerErrorService.insertPaperTrackingsError(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(paperTrackerTrackingService.updatePaperTrackingsStatus(eq(TRACKING_ID), any())).thenReturn(Mono.empty());
        when(eventBridgePublisher.publish(any())).thenReturn(Mono.just(PutEventsResponse.builder().build()));

        // Act
        StepVerifier.create(handler.execute(context)).verifyComplete();

        // Assert
        ArgumentCaptor<PaperTrackings> statusCaptor = ArgumentCaptor.forClass(PaperTrackings.class);
        verify(paperTrackerTrackingService).updatePaperTrackingsStatus(eq(TRACKING_ID), statusCaptor.capture());
        assertEquals(PaperTrackingsState.KO, statusCaptor.getValue().getState());
        verifyNoInteractions(paperTrackingsDAO);
    }

    private SendEvent captureSingleSentEvent() {
        ArgumentCaptor<PaperChannelUpdate> captor = ArgumentCaptor.forClass(PaperChannelUpdate.class);
        verify(eventBridgePublisher, times(1)).publish(captor.capture());
        return captor.getValue().getSendEvent();
    }

    private HandlerContext buildContext(boolean finalEventFlow, PaperTrackingsState state) {
        Event event = new Event();
        event.setId(EVENT_ID);
        event.setStatusCode(FINAL_STATUS_CODE);

        PaperStatus paperStatus = new PaperStatus();
        paperStatus.setDeliveryFailureCause("M10");

        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setTrackingId(TRACKING_ID);
        paperTrackings.setAttemptId("PREPARE_ANALOG_DOMICILE.IUN_XXXX-XXXX-XXXX-202410-X-1.RECINDEX_0.ATTEMPT_0");
        paperTrackings.setProductType(ProductType.AR.getValue());
        paperTrackings.setState(state);
        paperTrackings.setBusinessState(BusinessState.AWAITING_FINAL_STATUS_CODE);
        paperTrackings.setPaperStatus(paperStatus);
        paperTrackings.setEvents(List.of(event));

        SendEvent finalEvent = new SendEvent();
        finalEvent.setStatusCode(StatusCodeEnum.KO);
        finalEvent.setStatusDetail(FINAL_STATUS_CODE);

        HandlerContext context = new HandlerContext();
        context.setTrackingId(TRACKING_ID);
        context.setPaperTrackings(paperTrackings);
        context.setEventId(EVENT_ID);
        context.setFinalStatusCode(FINAL_STATUS_CODE);
        context.setEventsToSend(new ArrayList<>(List.of(finalEvent)));

        if (finalEventFlow) {
            PaperProgressStatusEvent paperProgressStatusEvent = new PaperProgressStatusEvent();
            paperProgressStatusEvent.setRequestId(TRACKING_ID);
            paperProgressStatusEvent.setStatusCode(FINAL_STATUS_CODE);
            context.setPaperProgressStatusEvent(paperProgressStatusEvent);
        }
        return context;
    }

    private PcRetryResponse retryNotFound() {
        PcRetryResponse response = new PcRetryResponse();
        response.setRetryFound(false);
        response.setParentRequestId(TRACKING_ID);
        return response;
    }

    private PcRetryResponse retryFound() {
        PcRetryResponse response = new PcRetryResponse();
        response.setRetryFound(true);
        response.setParentRequestId(TRACKING_ID);
        response.setRequestId(NEXT_TRACKING_ID);
        response.setPcRetry("PCRETRY_1");
        return response;
    }
}
