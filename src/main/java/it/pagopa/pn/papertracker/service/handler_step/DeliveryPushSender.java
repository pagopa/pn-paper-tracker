package it.pagopa.pn.papertracker.service.handler_step;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.mapper.ExternalChannelOutputEventMapper;
import it.pagopa.pn.papertracker.mapper.PaperTrackerDryRunOutputsMapper;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackerDryRunOutputsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Event;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.queue.producer.ExternalChannelOutputsMomProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeliveryPushSender {

    private final PnPaperTrackerConfigs configs;
    private final PaperTrackerDryRunOutputsDAO paperTrackerDryRunOutputsDAO;
    private final ExternalChannelOutputsMomProducer externalChannelOutputsMomProducer;


    /**
     * Invia i dati specificati al target di output configurato.
     * <p>
     * I target possono essere o la coda pn-external_channel_outputs o la tabella PnPaperTrackerDryRunOutputs
     * Per configurare il target di output si utilizza un flag sendToDeliveryPushFlag
     *
     * @param event             evento da salvare nel target di output
     * @param paperTrackings    L'ogetto di paperTracking
     */
    public void sendToOutputTarget(Event event, PaperTrackings paperTrackings) {
        log.info("Sending delivery push for event: {}, paperTrackings: {}", event, paperTrackings);
        if (configs.isSendOutputToDeliveryPush()) {
            log.info("Sending event to pn-external_channel_outputs");
            externalChannelOutputsMomProducer.push(ExternalChannelOutputEventMapper.buildExternalChannelOutputEvent(event, paperTrackings));
        } else {
            log.info("Sending event to PnPaperTrackerDryRunOutputs");
            paperTrackerDryRunOutputsDAO.insertOutputEvent(PaperTrackerDryRunOutputsMapper.buildDryRunOutput(event, paperTrackings));
        }
    }

}
