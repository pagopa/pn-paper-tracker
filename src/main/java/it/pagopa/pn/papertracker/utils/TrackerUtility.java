package it.pagopa.pn.papertracker.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TrackerUtility {

    public static String getIunFromRequestId(String requestId) {
        if (requestId == null) return null;
        Pattern pattern = Pattern.compile("IUN_(.*?)\\.");
        Matcher matcher = pattern.matcher(requestId);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
