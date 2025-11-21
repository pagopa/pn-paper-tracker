package it.pagopa.pn.papertracker.utils;

import it.pagopa.pn.papertracker.exception.PaperTrackerException;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.model.EventStatusCodeEnum;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.model.sequence.SequenceConfig;
import it.pagopa.pn.papertracker.model.sequence.SequenceConfiguration;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

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

    public static boolean checkIfIsRecag012event(String statusCode) {
        return RECAG012.name().equalsIgnoreCase(statusCode);
    }

    public static String buildOcrRequestId(String trackingId, String eventId, String documentType) {
        return String.join("#", trackingId, eventId, documentType);
    }

    public static String[] getParsedOcrCommandId(String ocrCommandId) {
        return ocrCommandId.split("#");
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
        if (RECAG012.name().equalsIgnoreCase(statusCode) || !isStock890SequenceStatusCodes(statusCode)) {
            PaperTrackingsState state = ctx.getPaperTrackings().getState();
            return state == PaperTrackingsState.DONE || state == PaperTrackingsState.AWAITING_OCR;
        } else {
            BusinessState businessState = ctx.getPaperTrackings().getBusinessState();
            return businessState == BusinessState.DONE || businessState == BusinessState.AWAITING_OCR;
        }
    }

    private static boolean isStock890SequenceStatusCodes(String statusCode) {
        SequenceConfig config005 = SequenceConfiguration.getConfig(RECAG005C.name());
        SequenceConfig config006 = SequenceConfiguration.getConfig(RECAG006C.name());
        SequenceConfig config007 = SequenceConfiguration.getConfig(RECAG007C.name());
        SequenceConfig config008 = SequenceConfiguration.getConfig(RECAG008C.name());
        return config005.sequenceStatusCodes().contains(statusCode) ||
                config006.sequenceStatusCodes().contains(statusCode) ||
                config007.sequenceStatusCodes().contains(statusCode) ||
                config008.sequenceStatusCodes().contains(statusCode);
    }

    public static boolean isInInvalidStateForOcr(PaperTrackings paperTrackings, String statusCode) {
        if (TrackerUtility.isStockStatus890(statusCode)) {
            BusinessState businessState = paperTrackings.getBusinessState();
            return businessState != BusinessState.AWAITING_OCR;
        } else{
            PaperTrackingsState state = paperTrackings.getState();
            return state != PaperTrackingsState.AWAITING_OCR;
        }
    }

    public static boolean isOcrResponseCompleted(ValidationFlow validationFlow, ValidationConfig validationConfig, String statusCode) {
        if(RECAG012.name().equalsIgnoreCase(statusCode)){
            List<String> requiredAttachments = validationConfig.getRequiredAttachmentsRefinementStock890();
            return validationFlow.getOcrRequests().stream()
                    .filter(ocrRequest -> requiredAttachments.contains(ocrRequest.getDocumentType()))
                    .noneMatch(ocrRequest -> Objects.isNull(ocrRequest.getResponseTimestamp()));
        }else if(TrackerUtility.isStockStatus890(statusCode)){
            List<String> requiredAttachments = validationConfig.getSendOcrAttachmentsFinalValidationStock890();
            return validationFlow.getOcrRequests().stream()
                    .filter(ocrRequest -> requiredAttachments.contains(ocrRequest.getDocumentType()))
                    .noneMatch(ocrRequest -> Objects.isNull(ocrRequest.getResponseTimestamp()));
        }else{
            List<String> requiredAttachments = validationConfig.getSendOcrAttachmentsFinalValidation();
            return validationFlow.getOcrRequests().stream()
                    .filter(ocrRequest -> requiredAttachments.contains(ocrRequest.getDocumentType()))
                    .noneMatch(ocrRequest -> Objects.isNull(ocrRequest.getResponseTimestamp()));
        }
    }

    public static Event extractEventFromContext(HandlerContext context) {
        return context.getPaperTrackings().getEvents().stream()
                .filter(event -> context.getEventId().equalsIgnoreCase(event.getId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("The event with id " + context.getEventId() + " does not exist in the paperTrackings events list."));
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

    public static Event extractFinalEventFromOcr(String commandId, PaperTrackings paperTrackings) {
        String eventId = TrackerUtility.getParsedOcrCommandId(commandId)[1];
        return paperTrackings.getEvents().stream()
                .filter(event -> eventId.equalsIgnoreCase(event.getId()))
                .findFirst()
                .orElseThrow(() -> new PaperTrackerException("Invalid eventId in ocrCommandId: " + eventId +
                        ". The event with id " + eventId + " does not exist in the paperTrackings events list."));
    }

    public static Integer getOcrRequestIndexByEventIdAndDocType(PaperTrackings tracking, String eventId, String docType) {
        List<OcrRequest> ocrRequests = tracking.getValidationFlow().getOcrRequests();
        if(CollectionUtils.isEmpty(ocrRequests)){
            return null;
        }
        return IntStream.range(0, tracking.getValidationFlow().getOcrRequests().size())
                        .filter(index -> ocrRequests.get(index).getEventId().equalsIgnoreCase(eventId) && ocrRequests.get(index).getDocumentType().equalsIgnoreCase(docType))
                        .findFirst()
                        .orElse(-1);
    }

    public static Attachment getAttachmentFromEventIdAndDocType(PaperTrackings tracking, String eventId, String docType) {
        List<OcrRequest> ocrRequests = tracking.getValidationFlow().getOcrRequests();
        if(CollectionUtils.isEmpty(ocrRequests)){
            return null;
        }
        return tracking.getValidationFlow().getOcrRequests().stream()
                .filter(req -> req.getEventId().equalsIgnoreCase(eventId) && req.getDocumentType().equalsIgnoreCase(docType))
                .findFirst()
                .map(ocrRequest -> {
                    Attachment attachment = new Attachment();
                    attachment.setDocumentType(docType);
                    attachment.setUri(ocrRequest.getUri());
                    return attachment;
                })
                .orElse(null);
    }

    public static Optional<Event> findRECAG012Event(PaperTrackings paperTrackings) {
        return paperTrackings.getEvents().stream()
                .filter(event -> RECAG012.name().equalsIgnoreCase(event.getStatusCode()))
                .findFirst();
    }
}
