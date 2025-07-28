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


    /**
     * Riceve i messaggi provenienti dalla coda pn-external_channel_to_paper_tracker e gestisce, secondo il productType
     * e statusCode, l'evento processandolo secondo l'handler adatto.
     *
     * @param payload il SingleStatusUpdate contenente le informazioni da processare
     */
    public void handleExternalChannelMessage(SingleStatusUpdate payload) {
        String statusCode = payload.getAnalogMail() != null ? payload.getAnalogMail().getStatusCode() : "";
        String productType = StatusCodeConfiguration.StatusCodeConfigurationEnum.valueOf(statusCode)
                .getProductType().getValue();

        log.info("Handling external channel message with statusCode: {}, productType: {}",
                statusCode, productType);

        if (ProductType.AR.getValue().equals(productType)) {
            handleAREvent(payload, statusCode);
        }
    }

    /**
     * Gestisce gli eventi di tipo AR (Analogue Mail) in base allo statusCode.
     * A seconda dello statusCode, invoca l'handler appropriato per gestire l'evento.
     *
     * @param payload il SingleStatusUpdate contenente le informazioni da processare
     * @param statusCode lo statusCode dell'evento
     */
    private void handleAREvent(SingleStatusUpdate payload, String statusCode){
        ExternalChannelOutputsPayload.StatusCode status = statusCodeConfiguration.getStatusFromStatusCode(statusCode);
        HandlerContext context = new HandlerContext();
        context.setPaperProgressStatusEvent(payload.getAnalogMail());

        switch (status) {
            case PROGRESS:
                log.debug("Handling PROGRESS statusCode");
                handlersFactoryAr.buildIntermediateEventsHandler(context);
                break;
            case KO:
                log.debug("Handling KO statusCode");
                handlersFactoryAr.buildRetryEventHandler(context);
                break;
            case OK:
                log.debug("Handling OK statusCode");
                handlersFactoryAr.buildFinalEventsHandler(context);
                break;
            default:
                log.error("Unhandled status code: {}", status);
        }
    }
}
