package it.pagopa.pn.papertracker.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TrackerUtilityTest {

    @Test
    void getIunFromRequestIdReturnsCorrectIun() {
        String requestId = "PREPARE_ANALOG_DOMICILE.IUN_TMWL-NMRG-MKJK-202509-Q-1.RECINDEX_0.ATTEMPT_0.PCRETRY_0";
        String result = TrackerUtility.getIunFromRequestId(requestId);
        Assertions.assertEquals("TMWL-NMRG-MKJK-202509-Q-1", result);
    }

    @Test
    void getIunFromRequestIdReturnsNullForNullInput() {
        String result = TrackerUtility.getIunFromRequestId(null);
        Assertions.assertNull(result);
    }

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
}
