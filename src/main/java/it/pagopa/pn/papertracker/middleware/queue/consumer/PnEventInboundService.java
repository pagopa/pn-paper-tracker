package it.pagopa.pn.papertracker.middleware.queue.consumer;

import io.awspring.cloud.sqs.annotation.SqsListener;
import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SingleStatusUpdate;
import it.pagopa.pn.papertracker.middleware.queue.consumer.internal.ExternalChannelHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PnEventInboundService {

    private final ExternalChannelHandler externalChannelHandler;

    @SqsListener(value = "${pn.paper-tracker.topics.external-channel-to-paper-tracker}")
    public void externalChannelConsumer(@Payload Message<SingleStatusUpdate> message, @Headers Map<String, Object> headers) {
        try {
            log.debug("Handle message from pn-external_channel_to_paper_tracker with message {}", message);
            externalChannelHandler.handleExternalChannelMessage(message.getPayload());

        } catch (Exception ex) {
            log.error("Error processing external channel result message: {}", ex.getMessage(), ex);
        }
    }

}
