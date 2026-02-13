package it.pagopa.pn.papertracker.service.handler_step._890;

import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SendEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.StatusCodeEnum;
import it.pagopa.pn.papertracker.mapper.SendEventMapper;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.model.*;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import it.pagopa.pn.papertracker.utils.TrackerUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static it.pagopa.pn.papertracker.model.EventStatusCodeEnum.RECAG012;
import static it.pagopa.pn.papertracker.model.EventStatusCodeEnum.RECAG012A;

@Component
@RequiredArgsConstructor
@Slf4j
public class RECAG012EventBuilder implements HandlerStep {

    private final PaperTrackingsDAO paperTrackingsDAO;

    /**
     * Questo step viene invocato dai seguenti handler:
     * - StockIntermediateEventHandler (RECAG011B, RECAG005B, RECAG006B, RECAG007B, RECAG008B)
     * - Recag012EventHandler (RECAG012)
     * - OcrResponseHandler890 (RECAG012, RECAG001C, RECAG002C, RECAG003C,
     *   RECAG003F, RECAG005C, RECAG006C, RECAG007C, RECAG008C)
     *
     * Regole di esclusione:
     * Lo step non esegue alcuna azione se:
     * - Il tracking è già in stato DONE
     * - L’evento corrente è di tipo FINAL_EVENT
     *   (RECAG001C, RECAG002C, RECAG003C, RECAG003F,
     *    RECAG005C, RECAG006C, RECAG007C, RECAG008C)
     *
     * Gestione RECAG012A:
     * Il flag needToSendRECAG012A viene valorizzato a monte ed è TRUE se e solo se:
     * - Non sono arrivati tutti gli allegati obbligatori al refinement ma è arrivato il RECAG012
     * - L’handler chiamante non è OcrResponseHandler890
     *
     * Se il flag è TRUE:
     * - Viene inviato RECAG012A (status PROGRESS) solo se l’evento corrente è RECAG012
     * - Negli altri casi il flusso termina
     *
     * Gestione RECAG012 (flusso principale):
     *
     * Se needToSendRECAG012A è FALSE si distinguono due macro-scenari:
     *
     * 1) OCR DISABLED o DRY
     * - Se l’evento corrente è RECAG012 viene inviato RECAG012
     * - Se l’evento corrente è diverso viene inviato RECAG012
     *   utilizzando l’evento RECAG012 solo se già presente nel tracking
     *
     * 2) OCR RUN
     * - Se l’evento corrente è RECAG012 e tutte le risposte OCR
     *   relative agli allegati obbligatori sono arrivate, oppure non è previsto nessun allegato obbligatorio,
     *   viene inviato RECAG012
     * - Se le risposte OCR non sono complete il flusso termina
     */
    @Override
    public Mono<Void> execute(HandlerContext context) {

        PaperTrackings tracking = context.getPaperTrackings();
        String currentStatusCode = TrackerUtility.getStatusCodeFromEventId(tracking, context.getEventId());
        EventStatusCodeEnum eventStatus = EventStatusCodeEnum.fromKey(currentStatusCode);

        if (tracking.getState() == PaperTrackingsState.DONE || eventStatus.getCodeType() == EventTypeEnum.FINAL_EVENT) {
            log.info("Skip RECAG012 build for trackingId={} (state={}, status={})", context.getTrackingId(), tracking.getState(), currentStatusCode);
            return Mono.empty();
        }

        boolean isRecag012 = RECAG012.name().equalsIgnoreCase(currentStatusCode);
        boolean ocrDisabled = isOcrDisabledOrDry(tracking);
        boolean allOcrCompleted = checkAllOcrResponse(context);

        if (context.isNeedToSendRECAG012A()) {
            return isRecag012 ? sendRecag012A(context) : Mono.empty();
        }

        /*
         * =========================
         * OCR DISABLED / DRY
         * =========================
         */
        if (ocrDisabled) {
            return sendRecag012(context, isRecag012);
        }

        /*
         * =========================
         * OCR RUN
         * =========================
         */
        if (isRecag012 && allOcrCompleted) {
            return sendRecag012FromExisting(context);
        }

        return Mono.empty();
    }

    private boolean isOcrDisabledOrDry(PaperTrackings tracking) {
        OcrStatusEnum status = tracking.getValidationConfig().getOcrEnabled();
        return OcrStatusEnum.DISABLED.equals(status) || OcrStatusEnum.DRY.equals(status);
    }

    private Mono<Void> sendRecag012A(HandlerContext context) {
        return buildRecag012A(context)
                .doOnNext(context.getEventsToSend()::add)
                .then();
    }

    private Mono<Void> sendRecag012(HandlerContext context, boolean isRecag012) {
        Flux<SendEvent> events = isRecag012 ? buildFromPaperProgress(context) : buildFromExistingRecag012(context);
        return addEventToSendAndUpdateTracking(context, events);
    }

    private Mono<Void> sendRecag012FromExisting(HandlerContext context) {
        return addEventToSendAndUpdateTracking(context, buildFromContextEvent(context));
    }

    private Mono<Void> addEventToSendAndUpdateTracking(HandlerContext context, Flux<SendEvent> events) {
        return events
                .doOnNext(context.getEventsToSend()::add)
                .collectList()
                .filter(list -> !CollectionUtils.isEmpty(list))
                .flatMap(list -> paperTrackingsDAO.updateItem(context.getTrackingId(), buildTrackingUpdate())
                )
                .then();
    }

    private Flux<SendEvent> buildFromPaperProgress(HandlerContext context) {
        return SendEventMapper.createSendEventsFromPaperProgressStatusEvent(context.getPaperProgressStatusEvent());
    }

    private Flux<SendEvent> buildFromContextEvent(HandlerContext context) {
        Event recag012 = TrackerUtility.extractEventFromContext(context);
        return buildFromEventEntity(context, recag012);
    }

    private Flux<SendEvent> buildFromExistingRecag012(HandlerContext context) {
        Event recag012Event = findExistingRecag012(context.getPaperTrackings());
        return Objects.isNull(recag012Event) ? Flux.empty() : buildFromEventEntity(context, recag012Event);
    }

    private Flux<SendEvent> buildFromEventEntity(HandlerContext context,
                                                 Event event) {
        return SendEventMapper.createSendEventsFromEventEntity(
                context.getTrackingId(),
                event,
                StatusCodeEnum.OK,
                RECAG012.name(),
                event.getStatusTimestamp().atOffset(ZoneOffset.UTC)
        );
    }

    private Flux<SendEvent> buildRecag012A(HandlerContext context) {
        return SendEventMapper
                .createSendEventsFromPaperProgressStatusEvent(context.getPaperProgressStatusEvent())
                .doOnNext(event -> {
                    event.setStatusCode(StatusCodeEnum.PROGRESS);
                    event.setStatusDetail(RECAG012A.name());
                    event.setStatusDescription(RECAG012A.name());
                });
    }

    private Event findExistingRecag012(PaperTrackings tracking) {
        return Optional.ofNullable(tracking.getEvents())
                .orElse(List.of())
                .stream()
                .filter(e -> RECAG012.name().equalsIgnoreCase(e.getStatusCode()))
                .findFirst()
                .orElse(null);
    }

    private PaperTrackings buildTrackingUpdate() {
        ValidationFlow flow = new ValidationFlow();
        flow.setRefinementDematValidationTimestamp(Instant.now());
        PaperTrackings update = new PaperTrackings();
        update.setState(PaperTrackingsState.DONE);
        update.setValidationFlow(flow);
        return update;
    }

    // =========================================================
    // OCR RESPONSE CHECK
    // =========================================================

    private boolean checkAllOcrResponse(HandlerContext context) {

        PaperTrackings tracking = context.getPaperTrackings();
        ValidationConfig config = tracking.getValidationConfig();

        List<String> requiredDocs = Optional.ofNullable(config.getRequiredAttachmentsRefinementStock890()).orElse(List.of());

        if (requiredDocs.isEmpty()) {return true;}

        ValidationFlow flow = Optional.ofNullable(tracking.getValidationFlow()).orElse(new ValidationFlow());

        List<OcrRequest> ocrRequests = Optional.ofNullable(flow.getOcrRequests()).orElse(List.of());

        Set<String> completedDocs = ocrRequests.stream()
                .filter(req -> requiredDocs.contains(req.getDocumentType()))
                .filter(req -> req.getResponseTimestamp() != null)
                .map(OcrRequest::getDocumentType)
                .collect(Collectors.toSet());

        return TrackerUtility.hasRequiredAttachmentsRefinementStock890(requiredDocs, completedDocs);
    }
}