package it.pagopa.pn.papertracker.utils;

import it.pagopa.pn.papertracker.model.EventStatusCodeEnum;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.NONE)
public class TrackerUtility {

    private static final String P000event = "P000";

    public static String removePcretryFromRequestId(String requestId) {
        if (requestId == null) return null;
        return requestId.replaceAll("\\.PCRETRY_\\d+", "");
    }

    public static boolean checkIfIsFinalDemat(String eventStatusCode) {
        var parsedStatusCode = EventStatusCodeEnum.fromKey(eventStatusCode);
        return parsedStatusCode != null && parsedStatusCode.isFinalDemat();
    }

    public static boolean checkIfIsP000event(String eventStatusCode) {
        return P000event.equalsIgnoreCase(eventStatusCode);
    }

}
