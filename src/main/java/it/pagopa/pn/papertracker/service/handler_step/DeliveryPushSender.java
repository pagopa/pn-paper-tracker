package it.pagopa.pn.papertracker.service.handler_step;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.mapper.ExternalChannelOutputEventMapper;
import it.pagopa.pn.papertracker.mapper.PaperTrackerDryRunOutputsMapper;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackerDryRunOutputsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Event;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.queue.producer.ExternalChannelOutputsMomProducer;
import it.pagopa.pn.papertracker.model.HandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
                .zipWith(Mono.just(context.getPaperTrackings()))
                .flatMap(tuple -> sendToOutputTarget(tuple.getT1(), tuple.getT2()))
                .then();
    }

    /**
     * Invia i dati specificati al target di output configurato.
     * <p>
     * I target possono essere o la coda pn-external_channel_outputs o la tabella PnPaperTrackerDryRunOutputs
     * Per configurare il target di output si utilizza un flag sendToDeliveryPushFlag
     *
     * @param event             evento da salvare nel target di output
     * @param paperTrackings    L'ogetto di paperTracking
     */
    public Mono<Void> sendToOutputTarget(Event event, PaperTrackings paperTrackings) {
        return Mono.just(event)
                .zipWith(Mono.just(paperTrackings))
                .flatMap(tuple -> {
                    log.info("Sending delivery push for event: {}, paperTrackings: {}", event, paperTrackings);
                    if (configs.isSendOutputToDeliveryPush()) {
                        log.info("Sending event to pn-external_channel_outputs");
                        externalChannelOutputsMomProducer.push(ExternalChannelOutputEventMapper.buildExternalChannelOutputEvent(event, paperTrackings));
                        return Mono.empty();
                    } else {
                        log.info("Sending event to PnPaperTrackerDryRunOutputs");
                        return paperTrackerDryRunOutputsDAO.insertOutputEvent(PaperTrackerDryRunOutputsMapper.buildDryRunOutput(event, paperTrackings));
                    }
                })
                .then();
    }

}
