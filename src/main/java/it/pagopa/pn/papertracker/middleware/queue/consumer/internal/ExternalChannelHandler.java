package it.pagopa.pn.papertracker.middleware.queue.consumer.internal;

import com.sngular.apigenerator.asyncapi.business_model.model.event.ExternalChannelOutputsPayload;
import it.pagopa.pn.papertracker.config.StatusCodeConfiguration;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SingleStatusUpdate;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExternalChannelHandler {

    private final StatusCodeConfiguration statusCodeConfiguration;

    public void handleExternalChannelMessage(SingleStatusUpdate payload) {
        String statusCode = payload.getAnalogMail() != null ? payload.getAnalogMail().getStatusCode() : "";

        String productType = payload.getAnalogMail() != null ? payload.getAnalogMail().getProductType() : "";
        log.info("Handling external channel message with statusCode: {}, productType: {}",
                statusCode, payload.getAnalogMail().getProductType());

        if (ProductType.AR.getValue().equals(productType)) {
            handleAREvent(payload, statusCode);
        }
    }

    private void handleAREvent(SingleStatusUpdate payload, String statusCode){
        ExternalChannelOutputsPayload.StatusCode status = statusCodeConfiguration.getStatusFromStatusCode(statusCode);

        if (ExternalChannelOutputsPayload.StatusCode.PROGRESS.equals(status)) {
            log.debug("Handling PROGRESS statusCode");
            //TODO chiamare handler eventi intermedi
        } else if (ExternalChannelOutputsPayload.StatusCode.KO.equals(status)) {
            log.debug("Handling KO statusCode");
            //TODO chiamare handler eventi retry
        } else {
            log.debug("Handling OK statusCode");
            //TODO chiamare handler eventi finali
        }
    }
}
