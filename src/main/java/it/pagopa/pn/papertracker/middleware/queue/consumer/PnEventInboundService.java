package it.pagopa.pn.papertracker.middleware.queue.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sngular.apigenerator.asyncapi.business_model.model.event.OcrDataResultPayload;
import io.awspring.cloud.sqs.annotation.SqsListener;
import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.exception.PaperTrackerException;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SingleStatusUpdate;
import it.pagopa.pn.papertracker.middleware.queue.consumer.internal.ExternalChannelHandler;
import it.pagopa.pn.papertracker.middleware.queue.consumer.internal.InternalEventHandler;
import it.pagopa.pn.papertracker.utils.Utility;
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
    private final InternalEventHandler internalEventHandler;
    private final ObjectMapper objectMapper;

    @SqsListener(value = "${pn.paper-tracker.topics.external-channel-to-paper-tracker}")
    public void externalChannelConsumer(@Payload String message, @Headers Map<String, Object> headers) {
        try {
            log.debug("Handle message from pn-external_channel_to_paper_tracker with message {}", message);
            SingleStatusUpdate singleStatusUpdate = convertToObject(message, SingleStatusUpdate.class);
            externalChannelHandler.handleExternalChannelMessage(singleStatusUpdate);

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

    private <T> T convertToObject(String body, Class<T> tClass){
        T entity = Utility.jsonToObject(this.objectMapper, body, tClass);
        if (entity == null) throw new PaperTrackerException("MAPPER_ERROR");
        return entity;
    }

}
