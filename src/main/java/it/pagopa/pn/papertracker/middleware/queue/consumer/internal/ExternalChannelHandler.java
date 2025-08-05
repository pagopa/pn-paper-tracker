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

import java.util.Objects;
import java.util.Optional;

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
        if (Objects.isNull(payload) || Objects.isNull(payload.getAnalogMail())) {
            log.error("Received null payload or analogMail in ExternalChannelHandler");
            throw new IllegalArgumentException("Payload or analogMail cannot be null");
        }

        String processName = "processExternalChannelMessage";
        MDC.put(MDCUtils.MDC_PN_CTX_REQUEST_ID, payload.getAnalogMail().getRequestId());
        log.logStartingProcess(processName);

        MDCUtils.addMDCToContextAndExecute(Mono.just(payload)
                        .flatMap(singleStatusUpdate -> {
                            HandlerContext context = new HandlerContext();
                            context.setPaperProgressStatusEvent(payload.getAnalogMail());
                            String statusCode = payload.getAnalogMail().getStatusCode();
                            String productType = Optional.ofNullable(StatusCodeConfiguration.StatusCodeConfigurationEnum.fromKey(statusCode))
                                    .map(e -> e.getProductType().getValue())
                                    .orElse("UNKNOWN");
                            log.info("Handling external channel message with statusCode: {}, productType: {}",
                                    statusCode, productType);

                            if (ProductType.AR.getValue().equals(productType)) {
                                return handleAREvent(statusCode, context);
                            } else {
                                return handlersFactoryAr.buildUnrecognizedEventsHandler(context);
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
     * @param statusCode lo statusCode dell'evento
     */
    private Mono<Void> handleAREvent(String statusCode, HandlerContext context) {
        EventStatus status = statusCodeConfiguration.getStatusFromStatusCode(statusCode);

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
        };
    }
}
