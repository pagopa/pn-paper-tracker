package it.pagopa.pn.papertracker.middleware.queue.consumer;

import io.awspring.cloud.sqs.annotation.SqsListener;
import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SingleStatusUpdate;
import it.pagopa.pn.papertracker.middleware.queue.consumer.internal.ExternalChannelHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PnEventInboundService {

    private final PnPaperTrackerConfigs cfg;
    private final ExternalChannelHandler externalChannelHandler;

    @SqsListener(queueNames = "${cfg.topics.externalChannelToPaperTracker}")
    public void externalChannelConsumer(Message<SingleStatusUpdate> message) {
        try {
            log.debug("Handle message from pn-external_channel_to_paper_tracker with message {}", message);
            externalChannelHandler.handleExternalChannelMessage(message.getPayload());

        } catch (Exception ex) {
            log.error("Error processing external channel result message: {}", ex.getMessage(), ex);
        }
    }

}
