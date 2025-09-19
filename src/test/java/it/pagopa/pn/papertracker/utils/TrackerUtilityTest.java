package it.pagopa.pn.papertracker.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TrackerUtilityTest {

    @Test
    void removePcretryFromRequestIdRemovesPcretry() {
        String requestId = "PREPARE_ANALOG_DOMICILE.IUN_TMWL-NMRG-MKJK-202509-Q-1.RECINDEX_0.ATTEMPT_0.PCRETRY_0";
        String result = TrackerUtility.removePcretryFromRequestId(requestId);
        Assertions.assertEquals("PREPARE_ANALOG_DOMICILE.IUN_TMWL-NMRG-MKJK-202509-Q-1.RECINDEX_0.ATTEMPT_0", result);
    }

    @Test
    void removePcretryFromRequestIdReturnsSameStringIfNoPcretry() {
        String requestId = "PREPARE_ANALOG_DOMICILE.IUN_TMWL-NMRG-MKJK-202509-Q-1.RECINDEX_0.ATTEMPT_0";
        String result = TrackerUtility.removePcretryFromRequestId(requestId);
        Assertions.assertEquals(requestId, result);
    }

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
}
