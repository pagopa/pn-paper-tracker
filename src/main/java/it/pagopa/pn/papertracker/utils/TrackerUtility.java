package it.pagopa.pn.papertracker.utils;

import it.pagopa.pn.papertracker.model.EventStatus;
import it.pagopa.pn.papertracker.model.EventStatusCodeEnum;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

import static it.pagopa.pn.papertracker.model.EventStatusCodeEnum.P000;
import static it.pagopa.pn.papertracker.model.EventStatusCodeEnum.RECRN002C;

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

    public static EventStatus evaluateStatusCodeAndRetrieveStatus(String statusCode, String deliveryFailureCause) {
        if(RECRN002C.name().equalsIgnoreCase(statusCode)) {
            if (StringUtils.equals("M02", deliveryFailureCause) || StringUtils.equals("M05", deliveryFailureCause)) {
                return EventStatus.OK;
            }
            if (StringUtils.equals("M06", deliveryFailureCause) || StringUtils.equals("M07", deliveryFailureCause) ||
                    StringUtils.equals("M08", deliveryFailureCause) || StringUtils.equals("M09", deliveryFailureCause)) {
                return EventStatus.KO;
            }
        }
        return Optional.ofNullable(EventStatusCodeEnum.fromKey(statusCode))
                .map(EventStatusCodeEnum::getStatus)
                .orElse(null);
    }
}
