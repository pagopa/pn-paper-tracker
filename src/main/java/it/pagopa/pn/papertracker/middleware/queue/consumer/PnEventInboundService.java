package it.pagopa.pn.papertracker.middleware.queue.consumer;

import com.sngular.apigenerator.asyncapi.business_model.model.event.OcrDataResultPayload;
import io.awspring.cloud.sqs.annotation.SqsListener;
import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.SingleStatusUpdate;
import it.pagopa.pn.papertracker.middleware.queue.consumer.internal.ExternalChannelHandler;
import it.pagopa.pn.papertracker.middleware.queue.consumer.internal.OcrEventHandler;
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
    private final PnPaperTrackerConfigs cfg;
    private final OcrEventHandler ocrEventHandler;

    @SqsListener(value = "${pn.paper-tracker.topics.external-channel-to-paper-tracker}")
    public void externalChannelConsumer(@Payload Message<SingleStatusUpdate> message, @Headers Map<String, Object> headers) {
        try {
            log.debug("Handle message from pn-external_channel_to_paper_tracker with message {} and headers {}", message, headers);
            boolean dryRunEnabled = Boolean.getBoolean((String) headers.getOrDefault("dryRun", "false"));
            externalChannelHandler.handleExternalChannelMessage(message.getPayload(), dryRunEnabled);

        } catch (Exception ex) {
            log.error("Error processing external channel result message: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    @SqsListener(value = "${pn.paper-tracker.topics.pn-ocr-outputs}")
    public void ocrOutputsConsumer(Message<OcrDataResultPayload> message) {
        try {
            log.debug("Handle message from pn-ocr_outputs with content {}", message);
            ocrEventHandler.handleOcrMessage(message.getPayload());

        } catch (Exception ex) {
            log.error("Error processing OCR result message: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

}
