package it.pagopa.pn.papertracker.utils;

import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Event;
import it.pagopa.pn.papertracker.model.EventStatusCodeEnum;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.util.List;

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

    public static String buildOcrRequestId(String trackingId, String eventId) {
        return String.join("#", trackingId, eventId);
    }

    public static String getEventIdFromOcrRequestId(String ocrRequestId) {
        return ocrRequestId.split("#")[1];
    }

    public static List<Event> validatedEvents(List<String> eventsIds, List<Event> events) {
        return events.stream()
                .filter(event -> eventsIds.contains(event.getId()))
                .toList();
    }

}
