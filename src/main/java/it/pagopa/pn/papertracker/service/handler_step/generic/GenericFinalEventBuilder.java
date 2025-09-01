package it.pagopa.pn.papertracker.service.handler_step.generic;

import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SendEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.StatusCodeEnum;
import it.pagopa.pn.papertracker.mapper.SendEventMapper;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Event;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ValidationFlow;
import it.pagopa.pn.papertracker.middleware.msclient.DataVaultClient;
import it.pagopa.pn.papertracker.model.EventStatusCodeEnum;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

import static it.pagopa.pn.papertracker.mapper.SendEventMapper.toAnalogAddress;

@Component
@Slf4j
@RequiredArgsConstructor
public class GenericFinalEventBuilder implements HandlerStep {

    private final DataVaultClient dataVaultClient;
    private final PaperTrackingsDAO paperTrackingsDAO;

    /**
     * Step che elabora l'evento finale in base alla logica di business definita.
     * Viene semplicemente aggiunto alla lista degli eventi da inviare.
     * @param context Contesto contenente le informazioni necessarie per l'elaborazione dell'evento.
     * @return Mono(Void)
     */
    @Override
    public Mono<Void> execute(HandlerContext context) {
        Event finalEvent = extractFinalEvent(context);
        return addEventToSend(context, finalEvent, EventStatusCodeEnum.fromKey(finalEvent.getStatusCode()).getStatus().name())
                .thenReturn(finalEvent)
                .doOnNext(event -> context.setFinalStatusCode(finalEvent.getStatusCode()))
                .map(sendEvent -> paperTrackingsDAO.updateItem(context.getPaperTrackings().getTrackingId(), getPaperTrackingsToUpdate()))
                .then();
    }

    protected Mono<Void> addEventToSend(HandlerContext ctx, Event finalEvent, String status) {
        return buildSendEvent(ctx, finalEvent, StatusCodeEnum.valueOf(status), finalEvent.getStatusCode(), finalEvent.getStatusTimestamp().atOffset(ZoneOffset.UTC))
                .doOnNext(event -> ctx.getEventsToSend().add(event))
                .then();
    }

    protected PaperTrackings getPaperTrackingsToUpdate() {
        PaperTrackings paperTrackings = new PaperTrackings();
        ValidationFlow validationFlow = new ValidationFlow();
        validationFlow.setFinalEventBuilderTimestamp(Instant.now());
        paperTrackings.setValidationFlow(validationFlow);
        return paperTrackings;
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

    protected Mono<SendEvent> enrichWithDiscoveredAddress(HandlerContext context, Event source, SendEvent sendEvent) {
        if (!StringUtils.hasText(context.getPaperTrackings().getPaperStatus().getDiscoveredAddress())) {
            return Mono.just(sendEvent);
        }

        if (Objects.nonNull(context.getPaperProgressStatusEvent()) &&
                Objects.nonNull(context.getPaperProgressStatusEvent().getDiscoveredAddress())) {
            sendEvent.setDiscoveredAddress(toAnalogAddress(context.getPaperProgressStatusEvent().getDiscoveredAddress()));
            return Mono.just(sendEvent);
        }

        return dataVaultClient.deAnonymizeDiscoveredAddress(context.getPaperTrackings().getTrackingId(), context.getPaperTrackings().getPaperStatus().getDiscoveredAddress())
                .map(SendEventMapper::toAnalogAddress)
                .doOnNext(sendEvent::setDiscoveredAddress)
                .doOnNext(analogAddress -> context.setAnonymizedDiscoveredAddressId(context.getPaperTrackings().getPaperStatus().getDiscoveredAddress()))
                .thenReturn(sendEvent);
    }


}
