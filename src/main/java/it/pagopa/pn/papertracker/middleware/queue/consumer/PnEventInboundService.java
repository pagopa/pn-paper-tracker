package it.pagopa.pn.papertracker.middleware.queue.consumer;

import com.sngular.apigenerator.asyncapi.business_model.model.event.OcrDataResultPayload;
import io.awspring.cloud.sqs.annotation.SqsListener;
import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SingleStatusUpdate;
import it.pagopa.pn.papertracker.middleware.queue.consumer.internal.ExternalChannelHandler;
import it.pagopa.pn.papertracker.middleware.queue.consumer.internal.InternalEventHandler;
import it.pagopa.pn.papertracker.middleware.queue.model.ExternalChannelOutcomeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PnEventInboundService {

    private final ExternalChannelHandler externalChannelHandler;
    private final PnPaperTrackerConfigs cfg;
    private final InternalEventHandler internalEventHandler;

    @SqsListener(value = "${pn.paper-tracker.topics.external-channel-to-paper-tracker}")
    public void externalChannelConsumer(ExternalChannelOutcomeEvent message) {
        try {
            log.debug("Handle message from pn-external_channel_to_paper_tracker with message {}", message);
            externalChannelHandler.handleExternalChannelMessage(message.getDetail());

        } catch (Exception ex) {
            log.error("Error processing external channel result message: {}", ex.getMessage(), ex);
        }
    }

    @SqsListener(value = "${pn.paper-tracker.topics.ocr-output-topic}")
    public void ocrOutputsConsumer(Message<OcrDataResultPayload> message) {
        try {
            log.debug("Handle message from pn-ocr_outputs with content {}", message);
            internalEventHandler.handleOcrMessage(message.getPayload());

        } catch (Exception ex) {
            log.error("Error processing OCR result message: {}", ex.getMessage(), ex);
        }
    }

}
