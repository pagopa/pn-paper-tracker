package it.pagopa.pn.papertracker.sqs.consumer;

import com.sngular.apigenerator.asyncapi.business_model.model.event.Ocr_data_result_payload;
import io.awspring.cloud.sqs.annotation.SqsListener;
import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.sqs.consumer.handler.internal.InternalEventHandler;
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

    //TODO mettere al posto del nome della coda il nome della coda configurata in application.yml
    @SqsListener(queueNames = "${cfg.topics.ocrOutputTopic}")
    public void ocrOutputsConsumer(Message<Ocr_data_result_payload> message) {
        try {
            log.debug("Handle message from pn-ocr_outputs with content {}", message);
            internalEventHandler.handleMessage(message.getPayload());

        } catch (Exception ex) {
            log.error("Error processing OCR result message: {}", ex.getMessage(), ex);
        }
    }

}
