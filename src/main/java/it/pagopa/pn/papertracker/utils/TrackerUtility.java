package it.pagopa.pn.papertracker.utils;

import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.model.EventStatusCodeEnum;
import it.pagopa.pn.papertracker.model.HandlerContext;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.List;

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

    public static boolean isStockStatus890(String status) {
        return RECAG005C.name().equalsIgnoreCase(status) ||
                RECAG006C.name().equalsIgnoreCase(status) ||
                RECAG007C.name().equalsIgnoreCase(status) ||
                RECAG008C.name().equalsIgnoreCase(status);
    }

    public static void setNewStatus(PaperTrackings paperTrackingsToUpdate, String statusCode, BusinessState businessState, PaperTrackingsState state) {
        if (RECAG012.name().equalsIgnoreCase(statusCode)) {
            paperTrackingsToUpdate.setState(state);
        } else if (TrackerUtility.isStockStatus890(statusCode)) {
            paperTrackingsToUpdate.setBusinessState(businessState);
        } else {
            paperTrackingsToUpdate.setState(state);
            paperTrackingsToUpdate.setBusinessState(businessState);
        }
    }

    public static void setDematValidationTimestamp(PaperTrackings paperTrackingsToUpdate, String statusCode) {
        ValidationFlow validationFlow = new ValidationFlow();
        if (RECAG012.name().equalsIgnoreCase(statusCode)) {
            validationFlow.setRefinementDematValidationTimestamp(Instant.now());
        } else if (TrackerUtility.isStockStatus890(statusCode)) {
            validationFlow.setFinalEventDematValidationTimestamp(Instant.now());
        } else {
            validationFlow.setRefinementDematValidationTimestamp(Instant.now());
            validationFlow.setFinalEventDematValidationTimestamp(Instant.now());
        }
        paperTrackingsToUpdate.setValidationFlow(validationFlow);
    }

    public static boolean isInvalidState(HandlerContext ctx, String statusCode) {
        if (TrackerUtility.isStockStatus890(statusCode)) {
            BusinessState businessState = ctx.getPaperTrackings().getBusinessState();
            return businessState == BusinessState.DONE || businessState == BusinessState.AWAITING_OCR;
        } else{
            PaperTrackingsState state = ctx.getPaperTrackings().getState();
            return state == PaperTrackingsState.DONE || state == PaperTrackingsState.AWAITING_OCR;
        }
    }

    public static String getStatusCodeFromEventId(PaperTrackings paperTrackings, String eventId) {
        if(!CollectionUtils.isEmpty(paperTrackings.getEvents())) {
            return paperTrackings.getEvents().stream()
                    .filter(event -> event.getId().equalsIgnoreCase(eventId))
                    .findFirst()
                    .map(Event::getStatusCode)
                    .orElse(null);
        }
        return null;
    }

}
