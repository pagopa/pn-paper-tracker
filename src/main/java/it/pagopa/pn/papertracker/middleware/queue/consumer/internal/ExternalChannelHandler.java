package it.pagopa.pn.papertracker.middleware.queue.consumer.internal;

import it.pagopa.pn.commons.utils.MDCUtils;
import it.pagopa.pn.papertracker.config.StatusCodeConfiguration;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SingleStatusUpdate;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.AR.HandlersFactoryAr;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@CustomLog
public class ExternalChannelHandler {

    private final HandlersFactoryAr handlersFactoryAr;
    private static final String HANDLING_INTERMEDIATE_EVENT_LOG = "Handling Intermediate event for productType: [{}] and event: [{}]";
    private static final String HANDLING_RETRYABLE_EVENT_LOG = "Handling Retryable event for productType: [{}] and event: [{}]";
    private static final String HANDLING_FINAL_EVENT_LOG = "Handling Final event for productType: [{}] and event: [{}]";

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
                            context.setEventId(UUID.randomUUID().toString());
                            String statusCode = payload.getAnalogMail().getStatusCode();
                            ProductType productType = ProductType.fromValue(Optional.ofNullable(StatusCodeConfiguration.StatusCodeConfigurationEnum.fromKey(statusCode))
                                    .map(e -> e.getProductType().getValue())
                                    .orElse("UNKNOWN"));
                            return switch (productType){
                                case AR -> handleAREvent(statusCode, context);
                                default -> handleUnrecognizedEventsHandler(context);
                            };
                        })
                        .doOnSuccess(resultFromAsync -> log.logEndingProcess(processName))
                )
                .block();
    }

    private Mono<Void> handleUnrecognizedEventsHandler(HandlerContext context) {
        return handlersFactoryAr.buildUnrecognizedEventsHandler(context);
    }

    /**
     * Gestisce gli eventi di tipo AR (Analogue Mail) in base allo statusCode.
     * A seconda dello statusCode, invoca l'handler appropriato per gestire l'evento.
     *
     * @param statusCode lo statusCode dell'evento
     */
    private Mono<Void> handleAREvent(String statusCode, HandlerContext context) {
        StatusCodeConfiguration.StatusCodeConfigurationEnum statusCodeConfigurationEnum = StatusCodeConfiguration.StatusCodeConfigurationEnum.fromKey(statusCode);
        return switch (statusCodeConfigurationEnum.getCodeType()) {
            case INTERMEDIATE_EVENT -> {
                log.info(HANDLING_INTERMEDIATE_EVENT_LOG, statusCodeConfigurationEnum.getProductType(), statusCode);
                yield handlersFactoryAr.buildIntermediateEventsHandler(context);
            }
            case RETRYABLE_EVENT -> {
                log.info(HANDLING_RETRYABLE_EVENT_LOG, statusCodeConfigurationEnum.getProductType(), statusCode);
                yield handlersFactoryAr.buildRetryEventHandler(context);
            }
            case FINAL_EVENT -> {
                log.info(HANDLING_FINAL_EVENT_LOG, statusCodeConfigurationEnum.getProductType(), statusCode);
                yield handlersFactoryAr.buildFinalEventsHandler(context);
            }
        };
    }
}
