package it.pagopa.pn.papertracker.service.handler_step;

import it.pagopa.pn.papertracker.config.StatusCodeConfiguration;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SendEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.StatusCodeEnum;
import it.pagopa.pn.papertracker.mapper.SendEventMapper;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Event;
import it.pagopa.pn.papertracker.middleware.msclient.DataVaultClient;
import it.pagopa.pn.papertracker.model.HandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

import static it.pagopa.pn.papertracker.mapper.SendEventMapper.toAnalogAddress;

@Component
@Slf4j
@RequiredArgsConstructor
public class GenericFinalEventBuilder implements HandlerStep {

    private final StatusCodeConfiguration statusCodeConfiguration;
    private final DataVaultClient dataVaultClient;

    @Override
    public Mono<Void> execute(HandlerContext context) {
        Event finalEvent = extractFinalEvent(context);
        return addEventToSend(context, finalEvent, getSendEventStatusCode(finalEvent.getStatusCode()));
    }

    protected Mono<Void> addEventToSend(HandlerContext ctx, Event finalEvent, String status) {
        return buildSendEvent(ctx, finalEvent, StatusCodeEnum.valueOf(status), finalEvent.getStatusCode(), finalEvent.getStatusTimestamp().atOffset(ZoneOffset.UTC))
                .doOnNext(event -> ctx.getEventsToSend().add(event))
                .then();
    }

    protected Flux<SendEvent> buildSendEvent(HandlerContext context,
                                             Event source,
                                             StatusCodeEnum status,
                                             String logicalStatus,
                                             OffsetDateTime ts) {
        return SendEventMapper.createSendEventsFromEventEntity(context.getTrackingId(), source, status, logicalStatus, ts)
                .flatMap(sendEvent -> enrichWithDiscoveredAddress(context, source, sendEvent));
    }

    protected Event extractFinalEvent(HandlerContext context) {
        return context.getPaperTrackings().getEvents().stream()
                .filter(event -> context.getEventId().equalsIgnoreCase(event.getId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("The event with id " + context.getEventId() + " does not exist in the paperTrackings events list."));
    }

    protected String getSendEventStatusCode(String statusCode) {
        return statusCodeConfiguration.getStatusFromStatusCode(statusCode).name();
    }

    protected Mono<SendEvent> enrichWithDiscoveredAddress(HandlerContext context, Event source, SendEvent sendEvent) {
        if (!StringUtils.hasText(source.getAnonymizedDiscoveredAddressId())) {
            return Mono.just(sendEvent);
        }

        if (Objects.nonNull(context.getPaperProgressStatusEvent()) &&
                Objects.nonNull(context.getPaperProgressStatusEvent().getDiscoveredAddress())) {
            sendEvent.setDiscoveredAddress(toAnalogAddress(context.getPaperProgressStatusEvent().getDiscoveredAddress()));
            return Mono.just(sendEvent);
        }

        return dataVaultClient.deAnonymizeDiscoveredAddress(context.getPaperTrackings().getTrackingId(), source.getAnonymizedDiscoveredAddressId())
                .map(SendEventMapper::toAnalogAddress)
                .doOnNext(sendEvent::setDiscoveredAddress)
                .thenReturn(sendEvent);
    }


}
