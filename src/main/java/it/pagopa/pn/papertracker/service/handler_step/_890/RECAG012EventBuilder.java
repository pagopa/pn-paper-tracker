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
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static it.pagopa.pn.papertracker.model.EventStatusCodeEnum.RECAG012;
import static it.pagopa.pn.papertracker.model.EventStatusCodeEnum.RECAG012A;

@Component
@RequiredArgsConstructor
@Slf4j
public class RECAG012EventBuilder implements HandlerStep {

    private final PaperTrackingsDAO paperTrackingsDAO;

    @Override
    public Mono<Void> execute(HandlerContext context) {
        PaperTrackings paperTrackings = context.getPaperTrackings();
        String statusCode = TrackerUtility.getStatusCodeFromEventId(paperTrackings, context.getEventId());
        String currentStatusCode = context.getPaperProgressStatusEvent().getStatusCode();
        PaperTrackingsState state = paperTrackings.getState();
        EventStatusCodeEnum eventStatus = EventStatusCodeEnum.fromKey(statusCode);

        if (state == PaperTrackingsState.DONE || eventStatus.getCodeType() == EventTypeEnum.FINAL_EVENT) {
            log.info("Skip RECAG012 event build for trackingId={} (state={}, statusCode={})", context.getTrackingId(), state, statusCode);
            return Mono.empty();
        }

        OcrStatusEnum ocrEnabled = paperTrackings.getValidationConfig().getOcrEnabled();
        if (context.isNeedToSendRECAG012A()) {
            return RECAG012.name().equalsIgnoreCase(currentStatusCode) ?
                    buildRECAG012AEvent(context).doOnNext(context.getEventsToSend()::add).then() : Mono.empty();
        }

        if (OcrStatusEnum.DISABLED.equals(ocrEnabled) || OcrStatusEnum.DRY.equals(ocrEnabled)) {
            return handleWithoutOcr(context, currentStatusCode);
        }
        return handleWithOcr(context);
    }

    private Mono<Void> handleWithoutOcr(HandlerContext context, String currentStatusCode) {
        return RECAG012.name().equalsIgnoreCase(currentStatusCode) ? buildAndUpdate(context, this::buildRECAG012Event)
                : buildAndUpdate(context, this::buildOkEventFromExisting);

    }

    private Mono<Void> handleWithOcr(HandlerContext context) {
        if (!checkAllOcrResponse(context)) return Mono.empty();
        return buildAndUpdate(context, this::buildRECAG012EventFromEventId);
    }

    private Event findExistingRECAG012Event(PaperTrackings paperTrackings) {
        return Objects.nonNull(paperTrackings.getEvents())
                ? paperTrackings.getEvents().stream()
                .filter(e -> RECAG012.name().equalsIgnoreCase(e.getStatusCode()))
                .findFirst()
                .orElse(null)
                : null;
    }

    private Mono<Void> buildAndUpdate(HandlerContext context, Function<HandlerContext, Flux<SendEvent>> builder) {
        return builder.apply(context)
                .doOnNext(context.getEventsToSend()::add)
                .collectList()
                .filter(sendEvents -> !CollectionUtils.isEmpty(sendEvents))
                .flatMap(list -> paperTrackingsDAO.updateItem(context.getTrackingId(), getPaperTrackingsToUpdate()))
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

    private Flux<SendEvent> buildRECAG012Event(HandlerContext context) {
        return SendEventMapper.createSendEventsFromPaperProgressStatusEvent(context.getPaperProgressStatusEvent());
    }

    private Flux<SendEvent> buildRECAG012EventFromEventId(HandlerContext context) {
        Event recag012Event = TrackerUtility.extractEventFromContext(context);
        return createEvents(context, recag012Event);
    }

    private Flux<SendEvent> buildOkEventFromExisting(HandlerContext context) {
        Event recag012Event = findExistingRECAG012Event(context.getPaperTrackings());
        if (Objects.isNull(recag012Event)) return Flux.empty();
        return createEvents(context, recag012Event);

    }

    private Flux<SendEvent> createEvents(HandlerContext context, Event recag012Event) {
        return SendEventMapper.createSendEventsFromEventEntity(
                context.getTrackingId(),
                recag012Event,
                StatusCodeEnum.OK,
                RECAG012.name(),
                recag012Event.getStatusTimestamp().atOffset(java.time.ZoneOffset.UTC)
        );
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
}
