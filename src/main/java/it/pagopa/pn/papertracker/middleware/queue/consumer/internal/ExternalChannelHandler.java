package it.pagopa.pn.papertracker.middleware.queue.consumer.internal;

import com.sngular.apigenerator.asyncapi.business_model.model.event.ExternalChannelOutputsPayload;
import it.pagopa.pn.papertracker.config.StatusCodeConfiguration;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SingleStatusUpdate;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.AR.HandlersFactoryAr;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import it.pagopa.pn.papertracker.service.handler_step.RetrySender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExternalChannelHandler {

    private final StatusCodeConfiguration statusCodeConfiguration;
    private final HandlersFactoryAr handlersFactoryAr;


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
            handlersFactoryAr.buildEventsHandler(List.of(new RetrySender()), new HandlerContext());
        } else if (ExternalChannelOutputsPayload.StatusCode.KO.equals(status)) {
            log.debug("Handling KO statusCode");
            handlersFactoryAr.buildEventsHandler(List.of(new RetrySender()), new HandlerContext());
        } else {
            log.debug("Handling OK statusCode");
            handlersFactoryAr.buildEventsHandler(List.of(new RetrySender()), new HandlerContext());
        }
    }
}
