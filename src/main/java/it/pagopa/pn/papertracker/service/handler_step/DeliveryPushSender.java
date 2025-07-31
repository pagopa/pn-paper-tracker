package it.pagopa.pn.papertracker.service.handler_step;

import it.pagopa.pn.api.dto.events.GenericEventHeader;
import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PaperChannelUpdate;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SendEvent;
import it.pagopa.pn.papertracker.mapper.PaperTrackerDryRunOutputsMapper;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackerDryRunOutputsDAO;
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

    @Override
    public Mono<Void> execute(HandlerContext context) {
        return Flux.fromIterable(context.getEventsToSend())
                .zipWith(Mono.just(context))
                .flatMap(tuple -> sendToOutputTarget(tuple.getT1(), tuple.getT2().getAnonimizedDiscoveredAddress()))
                .then();
    }

    /**
     * Invia i dati specificati al target di output configurato.
     * <p>
     * I target possono essere o la coda pn-external_channel_outputs o la tabella PnPaperTrackerDryRunOutputs
     * Per configurare il target di output si utilizza un flag sendToDeliveryPushFlag
     *
     * @param event             evento da salvare nel target di output
     */
    public Mono<Void> sendToOutputTarget(SendEvent event, String discoveredAddress) {
        return Mono.just(event)
                .flatMap(tuple -> {
                    log.info("Sending delivery push for event: {}", event);
                    if (configs.isSendOutputToDeliveryPush()) {
                        if(StringUtils.hasText(discoveredAddress)){
                            //todo deanonimizzare l'indirizzo e set nel SendEvent
                        }
                        log.info("Sending event to pn-external_channel_outputs");
                        DeliveryPushEvent deliveryPushEvent = DeliveryPushEvent
                                .builder()
                                .payload(PaperChannelUpdate.builder().sendEvent(event).build())
                                .header( GenericEventHeader.builder()
                                        .publisher("pn-paper-tracking")
                                        .eventId(UUID.randomUUID().toString())
                                        .createdAt( Instant.now() )
                                        .eventType("")
                                        .build())
                                .build();
                        externalChannelOutputsMomProducer.push(deliveryPushEvent);
                        return Mono.empty();
                    } else {
                        log.info("Sending event to PnPaperTrackerDryRunOutputs");
                        return paperTrackerDryRunOutputsDAO.insertOutputEvent(PaperTrackerDryRunOutputsMapper.dtoToEntity(event, discoveredAddress));
                    }
                })
                .then();
    }

}
