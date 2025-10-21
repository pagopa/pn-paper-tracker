package it.pagopa.pn.papertracker.utils;

import it.pagopa.pn.papertracker.model.EventStatusCodeEnum;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import static it.pagopa.pn.papertracker.model.EventStatusCodeEnum.*;

@RequiredArgsConstructor(access = AccessLevel.NONE)
public class TrackerUtility {

    public static boolean checkIfIsFinalDemat(String eventStatusCode) {
        var parsedStatusCode = EventStatusCodeEnum.fromKey(eventStatusCode);
        return parsedStatusCode != null && parsedStatusCode.isFinalDemat();
    }

    public static boolean checkIfIsP000event(String eventStatusCode) {
        return P000.name().equalsIgnoreCase(eventStatusCode);
    }

    public static boolean checkIfIsInternalEvent(String eventStatusCode) {
        return P000.name().equalsIgnoreCase(eventStatusCode) ||
                P011.name().equalsIgnoreCase(eventStatusCode) ||
                P012.name().equalsIgnoreCase(eventStatusCode) ||
                P013.name().equalsIgnoreCase(eventStatusCode);
    }

    public static String buildOcrRequestId(String trackingId, String eventId) {
        return String.join("#", trackingId, eventId);
    }

    public static String getEventIdFromOcrRequestId(String ocrRequestId) {
        return ocrRequestId.split("#")[1];
    }

}
