package it.pagopa.pn.papertracker.middleware.queue.consumer;

import com.sngular.apigenerator.asyncapi.business_model.model.event.OcrDataResultPayload;
import io.awspring.cloud.sqs.annotation.SqsListener;
import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.middleware.queue.consumer.internal.InternalEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PnEventInboundService {

    private final PnPaperTrackerConfigs cfg;
    private final InternalEventHandler internalEventHandler;

    @SqsListener(queueNames = "${cfg.topics.ocrOutputTopic}")
    public void ocrOutputsConsumer(Message<OcrDataResultPayload> message) {
        try {
            log.debug("Handle message from pn-ocr_outputs with content {}", message);
            internalEventHandler.handleOcrMessage(message.getPayload());

        } catch (Exception ex) {
            log.error("Error processing OCR result message: {}", ex.getMessage(), ex);
        }
    }

}
