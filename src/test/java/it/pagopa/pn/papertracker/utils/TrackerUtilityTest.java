package it.pagopa.pn.papertracker.utils;

import it.pagopa.pn.papertracker.exception.PaperTrackerException;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Event;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
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
    void extractFinalEventFromOcrReturnsCorrectEvent() {
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setOcrRequestId("trackingId123#eventId456");
        Event event1 = new Event();
        event1.setId("eventId123");
        Event event2 = new Event();
        event2.setId("eventId456");
        paperTrackings.setEvents(List.of(event1, event2));

        Event result = TrackerUtility.extractFinalEventFromOcr(paperTrackings);
        Assertions.assertEquals("eventId456", result.getId());
    }

    @Test
    void extractFinalEventFromOcrThrowsExceptionForInvalidOcrRequestId() {
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setOcrRequestId("trackingId123#eventIdNotExist");
        Event event1 = new Event();
        event1.setId("eventId123");
        Event event2 = new Event();
        event2.setId("eventId456");
        paperTrackings.setEvents(List.of(event1, event2));

        Assertions.assertThrows(PaperTrackerException.class, () -> TrackerUtility.extractFinalEventFromOcr(paperTrackings));
    }

}
