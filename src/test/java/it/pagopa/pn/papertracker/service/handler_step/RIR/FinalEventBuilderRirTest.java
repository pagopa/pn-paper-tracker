package it.pagopa.pn.papertracker.service.handler_step.RIR;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.StatusCodeEnum;
import it.pagopa.pn.papertracker.generated.openapi.msclient.pndatavault.model.PaperAddress;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.middleware.msclient.DataVaultClient;
import it.pagopa.pn.papertracker.model.HandlerContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static it.pagopa.pn.papertracker.config.StatusCodeConfiguration.StatusCodeConfigurationEnum.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinalEventBuilderRirTest {

    @Mock
    PnPaperTrackerConfigs cfg;

    @Mock
    DataVaultClient dataVaultClient;

    FinalEventBuilderRir finalEventBuilder;

    HandlerContext handlerContext;

    PaperTrackings paperTrackings;

    private static final String EVENT_ID = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        handlerContext = new HandlerContext();
        handlerContext.setTrackingId("req-123");
        finalEventBuilder = new FinalEventBuilderRir(dataVaultClient);
        paperTrackings = new PaperTrackings();
        PaperStatus paperStatus = new PaperStatus();
        paperTrackings.setPaperStatus(paperStatus);
        paperTrackings.setTrackingId("req-123");
        paperTrackings.setProductType(ProductType.AR);
        paperTrackings.setUnifiedDeliveryDriver("POSTE");
        paperTrackings.setPaperStatus(new PaperStatus());
        paperTrackings.getPaperStatus().setRegisteredLetterCode("RL123");
        ValidationFlow validationFlow = new ValidationFlow();
        validationFlow.setSequencesValidationTimestamp(Instant.now());
        paperTrackings.setValidationFlow(validationFlow);
        Event event = new Event();
        event.setStatusCode("RECRI003A");
        event.setStatusTimestamp(now);
        event.setRequestTimestamp(now);
        event.setId(EVENT_ID + "1");

        Event event1 = new Event();
        event1.setStatusCode("RECRN003B");
        event1.setStatusTimestamp(now);
        event1.setRequestTimestamp(now);
        event1.setId(EVENT_ID + "2");

        Event event2 = new Event();
        event2.setStatusCode("RECRN003C");
        event2.setStatusTimestamp(now);
        event2.setRequestTimestamp(now);
        event2.setId(EVENT_ID);

        paperTrackings.setEvents(List.of(event, event1, event2));
        handlerContext.setPaperTrackings(paperTrackings);
    }

    @Test
    void buildRIRFinalEvent_withDiscoveredAddress() {
        // Arrange
        PaperProgressStatusEvent finalEvent = getFinalEvent(RECRI003C.name());
        handlerContext.setPaperProgressStatusEvent(finalEvent);
        handlerContext.setEventId(EVENT_ID);
        handlerContext.getPaperTrackings().getPaperStatus().setDiscoveredAddress("anonymized");
        Assertions.assertNull(handlerContext.getAnonymizedDiscoveredAddressId());

        PaperAddress paperAddress = new PaperAddress();
        paperAddress.setAddress("address");
        paperAddress.setCap("00100");
        paperAddress.setCity("Rome");
        paperAddress.setCountry("IT");
        paperAddress.setName("address name");

        when(dataVaultClient.deAnonymizeDiscoveredAddress(handlerContext.getTrackingId(), "anonymized"))
                .thenReturn(Mono.just(paperAddress));
        // Act
        StepVerifier.create(finalEventBuilder.execute(handlerContext))
                .verifyComplete();

        Assertions.assertEquals("anonymized", handlerContext.getAnonymizedDiscoveredAddressId());
        Assertions.assertEquals(1, handlerContext.getEventsToSend().size());
        Assertions.assertNotNull(handlerContext.getEventsToSend().getFirst().getDiscoveredAddress());
        Assertions.assertEquals(StatusCodeEnum.OK, handlerContext.getEventsToSend().getFirst().getStatusCode());
    }

    @Test
    void buildRIRFinalEvent_withoutDiscoveredAddress() {
        // Arrange
        PaperProgressStatusEvent finalEvent = getFinalEvent(RECRI003C.name());
        handlerContext.setEventId(EVENT_ID);
        handlerContext.setPaperProgressStatusEvent(finalEvent);

        // Act
        StepVerifier.create(finalEventBuilder.execute(handlerContext))
                .verifyComplete();

        Assertions.assertNull(handlerContext.getAnonymizedDiscoveredAddressId());
        Assertions.assertEquals(1, handlerContext.getEventsToSend().size());
        Assertions.assertNull(handlerContext.getEventsToSend().getFirst().getDiscoveredAddress());
        Assertions.assertEquals(StatusCodeEnum.OK, handlerContext.getEventsToSend().getLast().getStatusCode());
    }

    private PaperProgressStatusEvent getFinalEvent(String statusCode) {
        PaperProgressStatusEvent finalEvent = new PaperProgressStatusEvent();
        finalEvent.setStatusCode(statusCode);
        finalEvent.setRequestId("req-123");
        finalEvent.setClientRequestTimeStamp(OffsetDateTime.now());
        finalEvent.setRegisteredLetterCode("RL123");
        finalEvent.setDeliveryFailureCause("M02");
        finalEvent.setAttachments(new ArrayList<>());
        finalEvent.setStatusDateTime(OffsetDateTime.now());
        finalEvent.setProductType("AR");
        return finalEvent;
    }
}
