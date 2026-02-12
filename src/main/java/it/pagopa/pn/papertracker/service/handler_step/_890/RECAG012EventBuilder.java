package it.pagopa.pn.papertracker.service.handler_step._890;

import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SendEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.StatusCodeEnum;
import it.pagopa.pn.papertracker.mapper.SendEventMapper;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.model.EventStatusCodeEnum;
import it.pagopa.pn.papertracker.model.EventTypeEnum;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.model.OcrStatusEnum;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static it.pagopa.pn.papertracker.model.EventStatusCodeEnum.RECAG012;
import static it.pagopa.pn.papertracker.model.EventStatusCodeEnum.RECAG012A;
import static it.pagopa.pn.papertracker.utils.TrackerUtility.findRECAG012Event;

@Component
@RequiredArgsConstructor
@Slf4j
public class RECAG012EventBuilder implements HandlerStep {

    private final PaperTrackingsDAO paperTrackingsDAO;


    @Override
    public Mono<Void> execute(HandlerContext context) {
        log.info("Executing RECAG012EventBuilder step for trackingId: {}", context.getTrackingId());

        PaperTrackings paperTrackings = context.getPaperTrackings();
        // StatusCode dell'evento appena arrivato da pn-ec (RECAG011B o RECA00xB)
        // Oppure StatusCode dell'evento inviato all'OCR (RECAG012, RECAG005C, RECAG006C, RECAG007C, RECAG008C)
        String statusCode = TrackerUtility.getStatusCodeFromEventId(paperTrackings, context.getEventId());
        PaperTrackingsState state = paperTrackings.getState();
        EventStatusCodeEnum eventStatus = EventStatusCodeEnum.fromKey(statusCode);

        // Flusso OCR eventi RECAG005C, RECAG006C, RECAG007C, RECAG008C
        if (state == PaperTrackingsState.DONE || eventStatus.getCodeType() == EventTypeEnum.FINAL_EVENT) {
            log.info("Skip RECAG012 event build for trackingId={} (state={}, statusCode={})", context.getTrackingId(), state, statusCode);
            return Mono.empty();
        }

        if (context.isNeedToSendRECAG012A()) {
            // Caso 1: Non sono arrivati tutti gli allegati necessari ma è arrivato il RECAG012
            if(RECAG012.name().equalsIgnoreCase(statusCode)) {
                log.info("Building RECAG012A for trackingId: {}", context.getTrackingId());
                return buildRECAG012AEvent(context)
                        .doOnNext(context.getEventsToSend()::add)
                        .then();
            }
            // Caso 2: Non sono arrivati tutti gli allegati necessari e NON è arrivato il RECAG012
            return Mono.empty();
        }

        // Caso 3: Sono arrivati tutti gli allegati necessari ma non è arrivato il RECAG012
        Optional<Event> recag012Event = findRECAG012Event(context.getPaperTrackings());
        if(recag012Event.isEmpty()) {
            log.info("Skip RECAG012 FEEDBACK build, RECAG012 event not found");
            return Mono.empty();
        }

        // Caso 4: Sono arrivati tutti gli allegati necessari e il RECAG012

        // Se l'OCR è disabilitato o in DRY non resto in attesa della risposta e invio RECAG012
        OcrStatusEnum ocrEnabled = paperTrackings.getValidationConfig().getOcrEnabled();
        if (OcrStatusEnum.DISABLED.equals(ocrEnabled) || OcrStatusEnum.DRY.equals(ocrEnabled)) {
            return buildFeedbackAndUpdateEntity(context, recag012Event.get());
        }

        // Se l'OCR è in modalità RUN ma non ho mandato nessuna richiesta (allegati necessari vuoti)
        // non resto in attesa della risposta e invio RECAG012
        if (checkEmptyOcrRequests(context)) {
            return buildFeedbackAndUpdateEntity(context, recag012Event.get());
        }


        // Se l'OCR è in modalità RUN e ho richieste
        // Controllo tutte le risposte e invio RECAG012 FEEDBACK OK
        if (checkAllOcrResponse(context)){
            return buildFeedbackAndUpdateEntity(context, recag012Event.get());
        }

        return Mono.empty();
    }

    private Mono<Void> buildFeedbackAndUpdateEntity(HandlerContext context, Event recag012Event) {

        return SendEventMapper.createSendEventsFromEventEntity(
                        context.getTrackingId(),
                        recag012Event,
                        StatusCodeEnum.OK,
                        RECAG012.name(),
                        recag012Event.getStatusTimestamp().atOffset(ZoneOffset.UTC)
                )
                .doOnNext(context.getEventsToSend()::add)
                .collectList()
                .filter(sendEvents -> !CollectionUtils.isEmpty(sendEvents))
                .flatMap(list ->
                        paperTrackingsDAO.updateItem(
                                context.getTrackingId(),
                                getPaperTrackingsToUpdate()
                        )
                )
                .then();
    }

    private PaperTrackings getPaperTrackingsToUpdate() {
        ValidationFlow validationFlow = new ValidationFlow();
        validationFlow.setRefinementDematValidationTimestamp(Instant.now());

        PaperTrackings update = new PaperTrackings();
        update.setState(PaperTrackingsState.DONE);
        update.setValidationFlow(validationFlow);
        return update;
    }

    private Flux<SendEvent> buildRECAG012AEvent(HandlerContext context) {
        return SendEventMapper.createSendEventsFromPaperProgressStatusEvent(context.getPaperProgressStatusEvent())
                .doOnNext(event -> {
                    event.setStatusCode(StatusCodeEnum.PROGRESS);
                    event.setStatusDetail(RECAG012A.name());
                    event.setStatusDescription(RECAG012A.name());
                });
    }

    private boolean checkAllOcrResponse(HandlerContext context) {
        ValidationConfig config = context.getPaperTrackings().getValidationConfig();
        List<String> requiredDocs = config.getRequiredAttachmentsRefinementStock890();
        List<OcrRequest> ocrRequests = context.getPaperTrackings().getValidationFlow().getOcrRequests();

        Set<String> completedDocs = ocrRequests.stream()
                .filter(req -> requiredDocs.contains(req.getDocumentType()))
                .filter(req -> Objects.nonNull(req.getResponseTimestamp()))
                .map(OcrRequest::getDocumentType)
                .collect(Collectors.toSet());

        return TrackerUtility.hasRequiredAttachmentsRefinementStock890(requiredDocs, completedDocs);
    }

    private boolean checkEmptyOcrRequests(HandlerContext context) {
        ValidationConfig config = context.getPaperTrackings().getValidationConfig();
        List<String> requiredDocs = config.getRequiredAttachmentsRefinementStock890();
        List<OcrRequest> ocrRequests = context.getPaperTrackings().getValidationFlow().getOcrRequests();

        // Ritorna true se NESSUNA richiesta OCR corrisponde ai documenti richiesti al refinement giacenza 890
        return ocrRequests.stream()
                .noneMatch(req -> requiredDocs.contains(req.getDocumentType()));
    }

}
