package it.pagopa.pn.papertracker.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sngular.apigenerator.asyncapi.business_model.model.event.PaperTrackerErrorPayloadDTO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.middleware.queue.model.PaperTrackerErrorEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static it.pagopa.pn.papertracker.utils.QueueConst.PAPER_TRACKER_ERROR_EVENT_TYPE;
import static it.pagopa.pn.papertracker.utils.QueueConst.PUBLISHER;

class PaperTrackerErrorEventMapperTest {

    @Test
    void toPaperTrackerErrorEventMapsAllFields() {
        PaperTrackingsErrors errors = PaperTrackingsErrors.builder()
                .trackingId("tracking1")
                .created(Instant.parse("2024-09-20T17:00:00Z"))
                .errorCategory(ErrorCategory.ATTACHMENTS_ERROR)
                .flowThrow(FlowThrow.DEMAT_VALIDATION)
                .eventThrow("RECRN001C")
                .eventIdThrow("event-1")
                .productType("890")
                .type(ErrorType.WARNING)
                .details(ErrorDetails.builder()
                        .cause(ErrorCause.VALUES_NOT_MATCHING)
                        .message("missing attachments")
                        .additionalDetails(Map.of("key", "value"))
                        .build())
                .build();

        PaperTrackerErrorEvent event = PaperTrackerErrorEventMapper.toPaperTrackerErrorEvent(errors);

        Assertions.assertEquals(PUBLISHER, event.getHeader().getPublisher());
        Assertions.assertEquals(PAPER_TRACKER_ERROR_EVENT_TYPE, event.getHeader().getEventType());
        Assertions.assertNotNull(event.getHeader().getEventId());
        Assertions.assertNotNull(event.getHeader().getCreatedAt());

        PaperTrackerErrorPayloadDTO payload = event.getPayload();
        Assertions.assertEquals(PaperTrackerErrorEventMapper.PAYLOAD_VERSION, payload.getVersion());
        Assertions.assertEquals("tracking1", payload.getTrackingId());
        Assertions.assertEquals("2024-09-20T17:00:00Z", payload.getCreated());
        Assertions.assertEquals("ATTACHMENTS_ERROR", payload.getCategory());
        Assertions.assertEquals("DEMAT_VALIDATION", payload.getFlowThrow());
        Assertions.assertEquals("RECRN001C", payload.getEventThrow());
        Assertions.assertEquals("event-1", payload.getEventIdThrow());
        Assertions.assertEquals("890", payload.getProductType());
        Assertions.assertEquals(PaperTrackerErrorPayloadDTO.Type.WARNING, payload.getType());
        Assertions.assertEquals("VALUES_NOT_MATCHING", payload.getDetails().getCause());
        Assertions.assertEquals("missing attachments", payload.getDetails().getMessage());
        Assertions.assertEquals(Map.of("key", "value"), payload.getDetails().getAdditionalDetails());
    }

    @Test
    void toPaperTrackerErrorEventHandlesNullOptionalFields() {
        PaperTrackingsErrors errors = PaperTrackingsErrors.builder()
                .trackingId("tracking1")
                .created(Instant.parse("2024-09-20T17:00:00Z"))
                .build();

        PaperTrackerErrorEvent event = PaperTrackerErrorEventMapper.toPaperTrackerErrorEvent(errors);

        Assertions.assertNull(event.getPayload().getCategory());
        Assertions.assertNull(event.getPayload().getType());
        Assertions.assertNull(event.getPayload().getFlowThrow());
        Assertions.assertNull(event.getPayload().getDetails());
    }

    @Test
    void paperTrackerErrorEventIsSerializable() throws Exception {
        PaperTrackingsErrors errors = PaperTrackingsErrors.builder()
                .trackingId("tracking1")
                .created(Instant.parse("2024-09-20T17:00:00Z"))
                .type(ErrorType.ERROR)
                .errorCategory(ErrorCategory.OCR_VALIDATION)
                .details(ErrorDetails.builder().cause(ErrorCause.OCR_KO).message("ko").build())
                .build();

        String json = new ObjectMapper().writeValueAsString(PaperTrackerErrorEventMapper.toPaperTrackerErrorEvent(errors).getPayload());

        Assertions.assertTrue(json.contains("\"trackingId\":\"tracking1\""));
        Assertions.assertTrue(json.contains("\"created\":\"2024-09-20T17:00:00Z\""));
        Assertions.assertTrue(json.contains("\"type\":\"ERROR\""));
        Assertions.assertTrue(json.contains("\"cause\":\"OCR_KO\""));
    }
}
