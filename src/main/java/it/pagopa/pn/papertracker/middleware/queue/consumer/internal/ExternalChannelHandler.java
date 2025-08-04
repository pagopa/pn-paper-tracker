package it.pagopa.pn.papertracker.middleware.queue.consumer.internal;

import it.pagopa.pn.commons.utils.MDCUtils;
import it.pagopa.pn.papertracker.config.StatusCodeConfiguration;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SingleStatusUpdate;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.model.EventStatus;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.AR.HandlersFactoryAr;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@CustomLog
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

        String processName = "processExternalChannelMessage";
        MDC.put(MDCUtils.MDC_PN_CTX_REQUEST_ID, payload.getAnalogMail().getRequestId());
        log.logStartingProcess(processName);

        MDCUtils.addMDCToContextAndExecute(Mono.just(payload)
                        .flatMap(singleStatusUpdate -> {
                            String statusCode = payload.getAnalogMail() != null ? payload.getAnalogMail().getStatusCode() : "";
                            String productType = StatusCodeConfiguration.StatusCodeConfigurationEnum.valueOf(statusCode)
                                    .getProductType().getValue();
                            log.info("Handling external channel message with statusCode: {}, productType: {}",
                                    statusCode, productType);

                            if (ProductType.AR.getValue().equals(productType)) {
                                return handleAREvent(payload, statusCode);
                            } else {
                                return Mono.empty();
                            }
                        })
                        .doOnSuccess(resultFromAsync -> log.logEndingProcess(processName))
                )
                .block();
    }

    /**
     * Gestisce gli eventi di tipo AR (Analogue Mail) in base allo statusCode.
     * A seconda dello statusCode, invoca l'handler appropriato per gestire l'evento.
     *
     * @param payload il SingleStatusUpdate contenente le informazioni da processare
     * @param statusCode lo statusCode dell'evento
     */
    private Mono<Void> handleAREvent(SingleStatusUpdate payload, String statusCode){
        EventStatus status = statusCodeConfiguration.getStatusFromStatusCode(statusCode);
        HandlerContext context = new HandlerContext();
        context.setPaperProgressStatusEvent(payload.getAnalogMail());

        return switch (status) {
            case PROGRESS -> {
                log.info("Handling PROGRESS statusCode");
                yield handlersFactoryAr.buildIntermediateEventsHandler(context);
            }
            case KO -> {
                log.info("Handling KO statusCode");
                yield handlersFactoryAr.buildRetryEventHandler(context);
            }
            case OK -> {
                log.info("Handling OK statusCode");
                yield handlersFactoryAr.buildFinalEventsHandler(context);
            }
            default -> {
                log.error("Unhandled status code: {}", status);
                yield Mono.empty();
            }
        };
    }
}
