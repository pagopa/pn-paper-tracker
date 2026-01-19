package it.pagopa.pn.papertracker.utils;

import it.pagopa.pn.papertracker.exception.PaperTrackerException;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.model.*;
import it.pagopa.pn.papertracker.model.sequence.SequenceConfig;
import it.pagopa.pn.papertracker.model.sequence.SequenceConfiguration;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
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

    public static boolean checkIfIsInternalEvent(List<String> internalEvents, String eventStatusCode) {
        return internalEvents.contains(eventStatusCode);
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
                .collect(Collectors.toMap(
                        Event::getId,
                        Function.identity(),
                        (existing, replacement) ->
                                existing.getCreatedAt().isAfter(replacement.getCreatedAt())
                                        ? existing
                                        : replacement
                ))
                .values()
                .stream()
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

    public static void checkValidationConfig(PaperTrackings paperTrackings) {
        //CONFIGURAZIONE DI DEFAULT PER TUTTE LE SPEDIZIONI INIZIALIZZATE PRIMA DEL RILASCIO DELL 890 (PN-17784)
        if(Objects.isNull(paperTrackings.getValidationConfig())){
            ValidationConfig validationConfig = new ValidationConfig();
            validationConfig.setOcrEnabled(OcrStatusEnum.DISABLED);
            validationConfig.setSendOcrAttachmentsFinalValidation(List.of(DocumentTypeEnum.PLICO.getValue(), DocumentTypeEnum.AR.getValue()));
            paperTrackings.setValidationConfig(validationConfig);
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
            List<String> requiredAttachments = validationConfig.getSendOcrAttachmentsRefinementStock890();
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
                        .filter(index -> ocrRequests.get(index).getFinalEventId().equalsIgnoreCase(eventId) && ocrRequests.get(index).getDocumentType().equalsIgnoreCase(docType))
                        .findFirst()
                        .orElse(-1);
    }

    public static Attachment getAttachmentFromEventIdAndDocType(PaperTrackings tracking, String eventId, String docType) {
        List<OcrRequest> ocrRequests = tracking.getValidationFlow().getOcrRequests();
        if(CollectionUtils.isEmpty(ocrRequests)){
            return null;
        }
        return tracking.getValidationFlow().getOcrRequests().stream()
                .filter(req -> req.getFinalEventId().equalsIgnoreCase(eventId) && req.getDocumentType().equalsIgnoreCase(docType))
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
    public static EventStatus evaluateStatusCodeAndRetrieveStatus(String statusCodeToEvaluate, String statusCode, PaperTrackings paperTrackings) {
        String deliveryFailureCause = paperTrackings.getPaperStatus().getDeliveryFailureCause();
        if (statusCodeToEvaluate.equalsIgnoreCase(statusCode)) {
            if (StringUtils.equals("M02", deliveryFailureCause) || StringUtils.equals("M05", deliveryFailureCause)) {
                return EventStatus.OK;
            }
            if (StringUtils.equals("M06", deliveryFailureCause) || StringUtils.equals("M07", deliveryFailureCause) ||
                    StringUtils.equals("M08", deliveryFailureCause) || StringUtils.equals("M09", deliveryFailureCause)) {
                return EventStatus.KO;
            }
        }
        return EventStatusCodeEnum.fromKey(statusCode).getStatus();
    }


    public static EventStatus evaluateStatusCodeAndRetrieveStatus(String statusCodeToEvaluate, String deliveryFailureCause, String productType) {

        EventStatusCodeEnum statusCodeEnum = EventStatusCodeEnum.fromKey(statusCodeToEvaluate);
        if (Objects.isNull(statusCodeEnum)) {
            return null;
        }

        if (!statusCodeEnum.getProductType().getValue().equalsIgnoreCase(productType) && statusCodeEnum.getProductType() != ProductType.RIR) {
            return null;
        }

        if (StringUtils.isNotBlank(deliveryFailureCause) && (statusCodeEnum.equals(EventStatusCodeEnum.RECAG003C) || statusCodeEnum.equals(EventStatusCodeEnum.RECRN002C))) {
            return switch (deliveryFailureCause) {
                case "M02", "M05" -> EventStatus.OK;
                case "M06", "M07", "M08", "M09" -> EventStatus.KO;
                default -> statusCodeEnum.getStatus();
            };
        }

        return statusCodeEnum.getStatus();
    }

    /**
     * Verifica se i tipi di documento forniti contengono tutti gli allegati richiesti
     * nel caso del perfezionamento della giacenza 890".
     * Se nella lista degli allegati obbligatori sono presenti sia "ARCAD" che "CAD",
     * verifica se almeno uno tra ARCAD e CAD è presente nei tipi di documento forniti
     *
     * @param requiredAttachments Una lista di stringhe che rappresentano gli allegati obbligatori richiesti.
     * @param documentTypes Un insieme di stringhe che rappresentano i tipi di documento disponibili.
     * @return {@code true} se i tipi di documento forniti soddisfano i requisiti degli allegati richiesti,
     *         {@code false} altrimenti.
     */
    public static boolean hasRequiredAttachmentsRefinementStock890(
            List<String> requiredAttachments,
            Set<String> documentTypes) {

        String arcad = DocumentTypeEnum.ARCAD.getValue();
        String cad = DocumentTypeEnum.CAD.getValue();

        boolean hasArcad = requiredAttachments.contains(arcad);
        boolean hasCad = requiredAttachments.contains(cad);

        // Caso ARCAD o CAD
        if (hasArcad && hasCad) {

            // Rimuove ARCAD e CAD dalla lista degli allegati obbligatori
            List<String> remaining = requiredAttachments.stream()
                    .filter(r -> !r.equals(arcad) && !r.equals(cad))
                    .toList();

            boolean hasRemaining = documentTypes.containsAll(remaining);
            boolean hasAtLeastOne = documentTypes.contains(arcad) || documentTypes.contains(cad);

            return hasRemaining && hasAtLeastOne;
        }

        // Caso standard
        return documentTypes.containsAll(requiredAttachments);
    }

    public static boolean checkIfIsRedrive(String senderId, List<String> redriveEnabledDomains) {
        if(StringUtils.isBlank(senderId) || CollectionUtils.isEmpty(redriveEnabledDomains)) {
            return false;
        }
        return redriveEnabledDomains.stream().anyMatch(senderId::endsWith);
    }

}
