package it.pagopa.pn.papertracker.service.handler_step._890;

import io.swagger.v3.oas.models.security.SecurityScheme;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SendEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.StatusCodeEnum;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.model.OcrStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static it.pagopa.pn.papertracker.model.EventStatusCodeEnum.RECAG012;
import static it.pagopa.pn.papertracker.model.EventStatusCodeEnum.RECAG012A;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RECAG012EventBuilderTest {

    @Mock
    private PaperTrackingsDAO paperTrackingsDAO;

    @InjectMocks
    private RECAG012EventBuilder recag012EventBuilder;

    private HandlerContext context;
    private PaperTrackings paperTrackings;

    @BeforeEach
    void setup() {
        paperTrackings = new PaperTrackings();
        paperTrackings.setTrackingId("T123");
        paperTrackings.setState(PaperTrackingsState.AWAITING_REFINEMENT);

        ValidationConfig config = new ValidationConfig();
        config.setOcrEnabled(OcrStatusEnum.DISABLED);
        paperTrackings.setValidationConfig(config);

        ValidationFlow flow = new ValidationFlow();
        paperTrackings.setValidationFlow(flow);

        context = new HandlerContext();
        context.setPaperTrackings(paperTrackings);
        context.setEventId("E123");
        context.setTrackingId("T123");
        context.setEventsToSend(new java.util.ArrayList<>());
        PaperProgressStatusEvent paperProgressStatusEvent = new PaperProgressStatusEvent();
        paperProgressStatusEvent.setRequestId("requestId");
        context.setPaperProgressStatusEvent(paperProgressStatusEvent);
    }

    @Test
    void shouldSkipWhenDone() {
        paperTrackings.setState(PaperTrackingsState.DONE);
        StepVerifier.create(recag012EventBuilder.execute(context))
                    .verifyComplete();

        verifyNoInteractions(paperTrackingsDAO);
    }

    @Test
    void shouldSkipWhenFinal() {
        paperTrackings.setState(PaperTrackingsState.AWAITING_REFINEMENT);
        Event event = new Event();
        event.setStatusCode("RECAG002C");
        event.setId("id");
        paperTrackings.setEvents(List.of(event));
        context.setEventId("id");
        StepVerifier.create(recag012EventBuilder.execute(context))
                .verifyComplete();

        verifyNoInteractions(paperTrackingsDAO);
    }

    @Test
    void shouldBuildRECAG012AEventWhenOcrDisabledWithoutRequiredAttachments() {
        paperTrackings.setState(PaperTrackingsState.AWAITING_OCR);
        Event event = new Event();
        event.setStatusCode("RECAG012");
        event.setStatusTimestamp(Instant.now());
        event.setRequestTimestamp(Instant.now());
        event.setId("id");
        PaperProgressStatusEvent paperProgressStatusEvent = new PaperProgressStatusEvent();
        paperProgressStatusEvent.setRequestId("requestId");
        paperProgressStatusEvent.setStatusCode("RECAG012");
        context.setPaperProgressStatusEvent(paperProgressStatusEvent);
        paperTrackings.setEvents(List.of(event));
        ValidationConfig validationConfig = new ValidationConfig();
        validationConfig.setOcrEnabled(OcrStatusEnum.DISABLED);
        paperTrackings.setValidationConfig(validationConfig);
        context.setNeedToSendRECAG012A(true);
        context.setEventId("id");

        StepVerifier.create(recag012EventBuilder.execute(context))
                    .verifyComplete();

        assert !context.getEventsToSend().isEmpty();
        SendEvent result = context.getEventsToSend().getFirst();
        assert result.getStatusCode() == StatusCodeEnum.PROGRESS;
        assert RECAG012A.name().equals(result.getStatusDetail());

        verifyNoInteractions(paperTrackingsDAO);
    }

    @Test
    void shouldBuildRECAG012AEventWhenOcrDisabledWithRequiredAttachments() {
        context.getPaperProgressStatusEvent().setStatusCode("RECAG012");
        paperTrackings.setState(PaperTrackingsState.AWAITING_OCR);
        Event event = new Event();
        event.setStatusCode("RECAG012");
        event.setId("id");
        paperTrackings.setEvents(List.of(event));
        ValidationConfig validationConfig = new ValidationConfig();
        validationConfig.setOcrEnabled(OcrStatusEnum.DISABLED);
        paperTrackings.setValidationConfig(validationConfig);

        context.setNeedToSendRECAG012A(false);
        context.setEventId("id");

        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.just(new PaperTrackings()));

        StepVerifier.create(recag012EventBuilder.execute(context))
                .verifyComplete();

        assert !context.getEventsToSend().isEmpty();
        SendEvent result = context.getEventsToSend().getFirst();
        assert result.getStatusCode() == StatusCodeEnum.OK;
        assert RECAG012.name().equals(result.getStatusDetail());
        verify(paperTrackingsDAO, times(1)).updateItem(eq("T123"), any(PaperTrackings.class));
    }

    @Test
    void shouldBuildRECAG012AEventWhenOcrDisabledWithRequiredAttachmentsWithBCurrentEvent() {
        paperTrackings.setState(PaperTrackingsState.AWAITING_OCR);
        Event event = new Event();
        event.setStatusCode("RECAG012");
        event.setStatusTimestamp(Instant.now());
        event.setRequestTimestamp(Instant.now());
        event.setId("id");
        Event event2 = new Event();
        event2.setStatusCode("RECAG011B");
        event2.setId("id2");
        paperTrackings.setEvents(List.of(event, event2));
        ValidationConfig validationConfig = new ValidationConfig();
        validationConfig.setOcrEnabled(OcrStatusEnum.DISABLED);
        paperTrackings.setValidationConfig(validationConfig);

        context.setNeedToSendRECAG012A(false);
        context.setEventId("id2");

        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.just(new PaperTrackings()));

        StepVerifier.create(recag012EventBuilder.execute(context))
                .verifyComplete();

        assert !context.getEventsToSend().isEmpty();
        SendEvent result = context.getEventsToSend().getFirst();
        assert result.getStatusCode() == StatusCodeEnum.OK;
        assert RECAG012.name().equals(result.getStatusDetail());
        verify(paperTrackingsDAO, times(1)).updateItem(eq("T123"), any(PaperTrackings.class));
    }

    @Test
    void shouldBuildRECAG012AEventWhenOcrEnabledWithoutAllResponse() {
        paperTrackings.setState(PaperTrackingsState.AWAITING_OCR);
        Event event = new Event();
        event.setStatusCode("RECAG012");
        event.setId("id");
        paperTrackings.setEvents(List.of(event));
        ValidationConfig validationConfig = new ValidationConfig();
        validationConfig.setOcrEnabled(OcrStatusEnum.RUN);
        validationConfig.setRequiredAttachmentsRefinementStock890(List.of("23L","ARCAD"));
        paperTrackings.setValidationConfig(validationConfig);
        ValidationFlow validationFlow = new ValidationFlow();
        OcrRequest ocrRequest = new OcrRequest();
        ocrRequest.setDocumentType("23L");
        ocrRequest.setResponseTimestamp(Instant.now());
        OcrRequest ocrRequest2 = new OcrRequest();
        ocrRequest2.setDocumentType("ARCAD");

        validationFlow.setOcrRequests(List.of(ocrRequest2, ocrRequest));
        paperTrackings.setValidationFlow(validationFlow);
        context.setEventId("id");

        StepVerifier.create(recag012EventBuilder.execute(context))
                .verifyComplete();

        assert context.getEventsToSend().isEmpty();
        verifyNoInteractions(paperTrackingsDAO);
    }

    @Test
    void shouldBuildRECAG012AEventWhenOcrEnabledWithAllResponse() {
        context.getPaperProgressStatusEvent().setStatusCode("RECAG012");
        paperTrackings.setState(PaperTrackingsState.AWAITING_OCR);
        Event event = new Event();
        event.setStatusCode("RECAG012");
        event.setRequestTimestamp(Instant.now());
        event.setStatusTimestamp(Instant.now());
        event.setId("id");
        paperTrackings.setEvents(List.of(event));
        ValidationConfig validationConfig = new ValidationConfig();
        validationConfig.setOcrEnabled(OcrStatusEnum.RUN);
        validationConfig.setRequiredAttachmentsRefinementStock890(List.of("23L","ARCAD"));
        paperTrackings.setValidationConfig(validationConfig);
        ValidationFlow validationFlow = new ValidationFlow();
        OcrRequest ocrRequest = new OcrRequest();
        ocrRequest.setDocumentType("23L");
        ocrRequest.setResponseTimestamp(Instant.now());
        OcrRequest ocrRequest2 = new OcrRequest();
        ocrRequest2.setDocumentType("ARCAD");
        ocrRequest2.setResponseTimestamp(Instant.now());

        validationFlow.setOcrRequests(List.of(ocrRequest2, ocrRequest));
        paperTrackings.setValidationFlow(validationFlow);
        context.setEventId("id");

        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.just(new PaperTrackings()));

        StepVerifier.create(recag012EventBuilder.execute(context))
                .verifyComplete();

        assert !context.getEventsToSend().isEmpty();
        SendEvent result = context.getEventsToSend().getFirst();
        assert result.getStatusCode() == StatusCodeEnum.OK;
        assert RECAG012.name().equals(result.getStatusDetail());
        verify(paperTrackingsDAO, times(1)).updateItem(eq("T123"), any(PaperTrackings.class));
    }


    @Test
    void shouldBuildRECAG012EventWhenOcrEnabledWithAllResponseWithBCurrentEvent() {
        paperTrackings.setState(PaperTrackingsState.AWAITING_OCR);
        Event event = new Event();
        event.setStatusCode("RECAG012");
        event.setStatusTimestamp(Instant.now());
        event.setRequestTimestamp(Instant.now());
        event.setId("id2");
        Event event2 = new Event();
        event2.setStatusCode("RECAG011B");
        event2.setId("id1");
        paperTrackings.setEvents(List.of(event,event2));
        ValidationConfig validationConfig = new ValidationConfig();
        validationConfig.setOcrEnabled(OcrStatusEnum.RUN);
        validationConfig.setRequiredAttachmentsRefinementStock890(List.of("23L","ARCAD"));
        paperTrackings.setValidationConfig(validationConfig);
        ValidationFlow validationFlow = new ValidationFlow();
        OcrRequest ocrRequest = new OcrRequest();
        ocrRequest.setDocumentType("23L");
        ocrRequest.setResponseTimestamp(Instant.now());
        OcrRequest ocrRequest2 = new OcrRequest();
        ocrRequest2.setDocumentType("ARCAD");
        ocrRequest2.setResponseTimestamp(Instant.now());

        validationFlow.setOcrRequests(List.of(ocrRequest2, ocrRequest));
        paperTrackings.setValidationFlow(validationFlow);
        context.setEventId("id2");

        when(paperTrackingsDAO.updateItem(any(), any())).thenReturn(Mono.just(new PaperTrackings()));

        StepVerifier.create(recag012EventBuilder.execute(context))
                .verifyComplete();

        assert !context.getEventsToSend().isEmpty();
        SendEvent result = context.getEventsToSend().getFirst();
        assert result.getStatusCode() == StatusCodeEnum.OK;
        assert RECAG012.name().equals(result.getStatusDetail());
        verify(paperTrackingsDAO, times(1)).updateItem(eq("T123"), any(PaperTrackings.class));
    }


}
