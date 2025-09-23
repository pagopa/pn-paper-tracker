package it.pagopa.pn.papertracker.utils;

import it.pagopa.pn.papertracker.model.EventStatusCodeEnum;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import static it.pagopa.pn.papertracker.model.EventStatusCodeEnum.P000;

@RequiredArgsConstructor(access = AccessLevel.NONE)
public class TrackerUtility {

    public static boolean checkIfIsFinalDemat(String eventStatusCode) {
        var parsedStatusCode = EventStatusCodeEnum.fromKey(eventStatusCode);
        return parsedStatusCode != null && parsedStatusCode.isFinalDemat();
    }

    public static boolean checkIfIsP000event(String eventStatusCode) {
        return P000.name().equalsIgnoreCase(eventStatusCode);
    }

}
