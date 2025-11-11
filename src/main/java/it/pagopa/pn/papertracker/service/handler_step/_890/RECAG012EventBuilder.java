package it.pagopa.pn.papertracker.service.handler_step._890;

import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.StatusCodeEnum;
import it.pagopa.pn.papertracker.mapper.SendEventMapper;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Event;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

import static it.pagopa.pn.papertracker.model.EventStatusCodeEnum.RECAG012A;

@Component
@RequiredArgsConstructor
@Slf4j
public class RECAG012EventBuilder implements HandlerStep {

    @Override
    public Mono<Void> execute(HandlerContext context) {
        log.info("RECAG012EventBuilder called for trackingId {}", context.getTrackingId());

        return context.getPaperTrackings().isRefined() ?
                logRequestRefined(context) : createAndSendRECAG012AEvent(context);
    }

    private Mono<Void> createAndSendRECAG012AEvent(HandlerContext context) {
        log.info("Request NOT refined, proceeding with RECAG012A handling for trackingId {}", context.getTrackingId());

        Event eventRECAG012 = context.getPaperTrackings().getEvents().stream()
                .filter(event -> context.getEventId().equalsIgnoreCase(event.getId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("The event RECAG012 with id " + context.getEventId() + " does not exist in the paperTrackings events list."));

        return SendEventMapper.createSendEventsFromEventEntity(
                        context.getTrackingId(), eventRECAG012, StatusCodeEnum.PROGRESS, RECAG012A.name(), OffsetDateTime.now())
                .doOnNext(context.getEventsToSend()::add)
                .then();
    }

    private Mono<Void> logRequestRefined(HandlerContext context) {
        log.info("Request is already refined, nothing to do for trackingId {}", context.getTrackingId());
        return Mono.empty();
    }
}
