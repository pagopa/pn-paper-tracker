package it.pagopa.pn.papertracker.service.handler_step;

import it.pagopa.pn.api.dto.events.GenericEventHeader;
import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PaperChannelUpdate;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SendEvent;
import it.pagopa.pn.papertracker.mapper.PaperTrackerDryRunOutputsMapper;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackerDryRunOutputsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperStatus;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsState;
import it.pagopa.pn.papertracker.middleware.queue.model.DeliveryPushEvent;
import it.pagopa.pn.papertracker.middleware.queue.producer.ExternalChannelOutputsMomProducer;
import it.pagopa.pn.papertracker.model.HandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Component
public class DeliveryPushSender implements HandlerStep {

    private final PnPaperTrackerConfigs configs;
    private final PaperTrackerDryRunOutputsDAO paperTrackerDryRunOutputsDAO;
    private final ExternalChannelOutputsMomProducer externalChannelOutputsMomProducer;

    /**
     * Step che esegue l'invio degli eventi al target di output configurato, coda verso delivery-push oppure tabella di dry-run
     * @param context Contesto contenente le informazioni necessarie per l'elaborazione dell'evento.
     * @return Mono(Void)
     */
    @Override
    public Mono<Void> execute(HandlerContext context) {
        return Flux.fromIterable(context.getEventsToSend())
                .flatMap(event -> sendToOutputTarget(event, context.getAnonymizedDiscoveredAddressId()))
                .map(sendEvent -> getPaperTrackingsDone(context.getPaperTrackings(), context.getFinalStatusCode()))
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
    public Mono<SendEvent> sendToOutputTarget(SendEvent event, String anonymizedDiscoveredAddressId) {
        return Mono.just(event)
                .flatMap(sendEvent -> {
                    log.info("Sending delivery push for event: {}", sendEvent);
                    if (configs.isSendOutputToDeliveryPush()) {
                        log.info("Sending event to pn-external_channel_outputs");
                        DeliveryPushEvent deliveryPushEvent = DeliveryPushEvent
                                .builder()
                                .payload(PaperChannelUpdate.builder().sendEvent(sendEvent).build())
                                .header( GenericEventHeader.builder()
                                        .publisher("pn-paper-tracking")
                                        .eventId(UUID.randomUUID().toString())
                                        .createdAt( Instant.now() )
                                        .eventType("SEND_EVENT_RESPONSE")
                                        .build())
                                .build();
                        externalChannelOutputsMomProducer.push(deliveryPushEvent);
                        return Mono.empty();
                    } else {
                        log.info("Sending event to PnPaperTrackerDryRunOutputs");
                        return paperTrackerDryRunOutputsDAO.insertOutputEvent(PaperTrackerDryRunOutputsMapper.dtoToEntity(sendEvent, anonymizedDiscoveredAddressId));
                    }
                })
                .thenReturn(event);
    }



    private PaperTrackings getPaperTrackingsDone(PaperTrackings contextPaperTrackings, String finalStatusCode) {
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setState(PaperTrackingsState.DONE);
        if(StringUtils.hasText(contextPaperTrackings.getNextRequestIdPcretry())){
            paperTrackings.setNextRequestIdPcretry(contextPaperTrackings.getNextRequestIdPcretry());
        }
        if(StringUtils.hasText(finalStatusCode)){
            PaperStatus paperStatus = new PaperStatus();
            paperStatus.setFinalStatusCode(finalStatusCode);
            paperTrackings.setPaperStatus(paperStatus );
        }
        return paperTrackings;
    }

}
