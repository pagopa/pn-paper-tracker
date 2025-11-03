package it.pagopa.pn.papertracker.utils;

import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Event;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class TrackerUtilityTest {

    @Test
    void checkIfIsFinalDematReturnsTrueForFinalDematStatusCode() {
        String eventStatusCode = "RECRN001B";
        Assertions.assertTrue(TrackerUtility.checkIfIsFinalDemat(eventStatusCode));
    }

    @Test
    void checkIfIsFinalDematReturnsFalseForNonFinalDematStatusCode() {
        String eventStatusCode = "RECRN002A";
        Assertions.assertFalse(TrackerUtility.checkIfIsFinalDemat(eventStatusCode));
    }

    @Test
    void checkIfIsP000eventReturnsTrueForP000StatusCode() {
        String eventStatusCode = "P000";
        Assertions.assertTrue(TrackerUtility.checkIfIsP000event(eventStatusCode));
    }

    @Test
    void checkIfIsP000eventReturnsFalseForNonP000StatusCode() {
        String eventStatusCode = "P001";
        Assertions.assertFalse(TrackerUtility.checkIfIsP000event(eventStatusCode));
    }

    @Test
    void buildOcrRequestIdReturnsCorrectFormat() {
        String trackingId = "trackingId123";
        String eventId = "eventId456";
        String result = TrackerUtility.buildOcrRequestId(trackingId, eventId);
        Assertions.assertEquals("trackingId123#eventId456", result);
    }

    @Test
    void getEventIdFromOcrRequestId() {
        String ocrRequestId = "trackingId123#eventId456";
        String result = TrackerUtility.getEventIdFromOcrRequestId(ocrRequestId);
        Assertions.assertEquals("eventId456", result);
    }

    @Test
    void validatedEventsReturnsMatchingEvents() {
        List<String> eventsIds = List.of("eventId1", "eventId2");
        Event event1 = new Event();
        event1.setId("eventId1");
        Event event2 = new Event();
        event2.setId("eventId2");
        Event event3 = new Event();
        event3.setId("eventId3");
        List<Event> events = List.of(event1, event2, event3);

        List<Event> result = TrackerUtility.validatedEvents(eventsIds, events);

        Assertions.assertEquals(2, result.size());
        Assertions.assertTrue(result.stream().anyMatch(event -> "eventId1".equals(event.getId())));
        Assertions.assertTrue(result.stream().anyMatch(event -> "eventId2".equals(event.getId())));
    }

}
