package it.pagopa.pn.papertracker.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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

}
