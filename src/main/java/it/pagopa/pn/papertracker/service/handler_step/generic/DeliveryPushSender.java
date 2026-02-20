package it.pagopa.pn.papertracker.service.handler_step.generic;

import it.pagopa.pn.api.dto.events.GenericEventHeader;
import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PaperChannelUpdate;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SendEvent;
import it.pagopa.pn.papertracker.mapper.PaperTrackerDryRunOutputsMapper;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackerDryRunOutputsDAO;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.BusinessState;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperStatus;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsState;
import it.pagopa.pn.papertracker.middleware.queue.model.DeliveryPushEvent;
import it.pagopa.pn.papertracker.middleware.queue.producer.ExternalChannelOutputsMomProducer;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import it.pagopa.pn.papertracker.utils.TrackerUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static it.pagopa.pn.papertracker.utils.QueueConst.DELIVERY_PUSH_EVENT_TYPE;
import static it.pagopa.pn.papertracker.utils.QueueConst.PUBLISHER;
import static it.pagopa.pn.papertracker.utils.TrackerUtility.setNewStatus;

@Service
@Slf4j
@RequiredArgsConstructor
@Component
public class DeliveryPushSender implements HandlerStep {

    private final PnPaperTrackerConfigs configs;
    private final PaperTrackerDryRunOutputsDAO paperTrackerDryRunOutputsDAO;
    private final PaperTrackingsDAO paperTrackingsDAO;
    private final ExternalChannelOutputsMomProducer externalChannelOutputsMomProducer;

    /**
     * Step che esegue l'invio degli eventi al target di output configurato, coda verso delivery-push oppure tabella di dry-run
     * @param context Contesto contenente le informazioni necessarie per l'elaborazione dell'evento.
     * @return Mono(Void)
     */
    @Override
    public Mono<Void> execute(HandlerContext context) {
        log.info("Executing DeliveryPushSender step for trackingId: {}", context.getTrackingId());

        List<SendEvent> filteredEvent = context.getEventsToSend().stream()
                .filter(sendEvent -> !configs.getSaveAndNotSendToDeliveryPush().contains(sendEvent.getStatusDetail()))
                .toList();

        if (CollectionUtils.isEmpty(filteredEvent)) {
            log.info("No events to send for trackingId: {}. Skipping DeliveryPushSender step.", context.getTrackingId());
            return Mono.empty();
        }

        return Flux.fromIterable(filteredEvent)
                .concatMap(sendEvent -> sendToOutputTarget(sendEvent, context))
                .collectList()
                .filter(sendEvent -> StringUtils.hasText(context.getFinalStatusCode()) || StringUtils.hasText(context.getPaperTrackings().getNextRequestIdPcretry()))
                .map(sendEvent -> getPaperTrackingsDone(context.getPaperTrackings(), context.getFinalStatusCode(), context.getEventId()))
                .flatMap(paperTrackings -> paperTrackingsDAO.updateItem(context.getTrackingId(), paperTrackings))
                .doOnNext(context::setPaperTrackings)
                .then();
    }

    /**
     * Invia i dati specificati al target di output configurato.
     * <p>
     * I target possono essere o la coda pn-external_channel_outputs o la tabella PnPaperTrackerDryRunOutputs
     * Per configurare il target di output si utilizza un flag sendToDeliveryPushFlag
     *
     * @param event evento da salvare nel target di output
     */
    public Mono<SendEvent> sendToOutputTarget(SendEvent event, HandlerContext context) {
        return Mono.just(event)
                .flatMap(sendEvent -> {
                    log.info("Sending delivery push for event: {}", sendEvent);
                    if (context.isDryRunEnabled()) {
                        log.info("Sending event to PnPaperTrackerDryRunOutputs");
                        return paperTrackerDryRunOutputsDAO.insertOutputEvent(PaperTrackerDryRunOutputsMapper.dtoToEntity(sendEvent, context.getAnonymizedDiscoveredAddressId()));
                    } else {
                        log.info("Sending event to pn-external_channel_outputs");
                        sendEvent.setRequestId(context.getPaperTrackings().getAttemptId());
                        DeliveryPushEvent deliveryPushEvent = DeliveryPushEvent
                                .builder()
                                .payload(PaperChannelUpdate.builder().sendEvent(sendEvent).build())
                                .header( GenericEventHeader.builder()
                                        .publisher(PUBLISHER)
                                        .eventId(UUID.randomUUID().toString())
                                        .createdAt( Instant.now() )
                                        .eventType(DELIVERY_PUSH_EVENT_TYPE)
                                        .build())
                                .build();
                        externalChannelOutputsMomProducer.push(deliveryPushEvent);
                        return Mono.empty();
                    }
                })
                .thenReturn(event);
    }

    private PaperTrackings getPaperTrackingsDone(PaperTrackings contextPaperTrackings, String finalStatusCode, String eventId) {
        PaperTrackings paperTrackings = new PaperTrackings();
        String statusCode = TrackerUtility.getStatusCodeFromEventId(contextPaperTrackings, eventId);
        setNewStatus(paperTrackings, finalStatusCode, BusinessState.DONE, PaperTrackingsState.DONE);
        if(StringUtils.hasText(contextPaperTrackings.getNextRequestIdPcretry())){
            paperTrackings.setNextRequestIdPcretry(contextPaperTrackings.getNextRequestIdPcretry());
        }
        if(StringUtils.hasText(finalStatusCode)){
            PaperStatus paperStatus = new PaperStatus();
            paperStatus.setFinalStatusCode(statusCode);
            paperTrackings.setPaperStatus(paperStatus);
        }
        return paperTrackings;
    }

}