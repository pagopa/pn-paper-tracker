package it.pagopa.pn.papertracker.utils;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.NONE)
public class TrackerUtility {

    public static String removePcretryFromRequestId(String requestId) {
        if (requestId == null) return null;
        return requestId.replaceAll("\\.PCRETRY_\\d+", "");
    }

}
