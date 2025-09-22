package it.pagopa.pn.papertracker.middleware.queue.consumer.internal;

import it.pagopa.pn.commons.utils.MDCUtils;
import it.pagopa.pn.papertracker.exception.PaperTrackerExceptionHandler;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.SingleStatusUpdate;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.model.EventStatusCodeEnum;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.AR.HandlersFactoryAr;
import it.pagopa.pn.papertracker.service.handler_step.RIR.HandlersFactoryRir;
import it.pagopa.pn.papertracker.service.handler_step.generic.AbstractHandlersFactory;
import it.pagopa.pn.papertracker.service.handler_step.generic.HandlersFactoryGeneric;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@CustomLog
public class ExternalChannelHandler {

    private final PaperTrackerExceptionHandler paperTrackerExceptionHandler;
    private final HandlersFactoryAr handlersFactoryAr;
    private final HandlersFactoryRir handlersFactoryRir;
    private final HandlersFactoryGeneric handlersFactoryGeneric;
    private static final String HANDLING_EVENT_LOG = "Handling {} event for productType: [{}] and event: [{}]";

    /**
     * Riceve i messaggi provenienti dalla coda pn-external_channel_to_paper_tracker e gestisce, secondo il productType
     * e statusCode, l'evento processandolo secondo l'handler adatto.
     *
     * @param payload il SingleStatusUpdate contenente le informazioni da processare
     */
    public void handleExternalChannelMessage(SingleStatusUpdate payload, boolean dryRunEnabled, String messageId) {
        if (Objects.isNull(payload) || Objects.isNull(payload.getAnalogMail())) {
            log.error("Received null payload or analogMail in ExternalChannelHandler");
            throw new IllegalArgumentException("Payload or analogMail cannot be null");
        }

        String processName = "processExternalChannelMessage";
        MDC.put(MDCUtils.MDC_PN_CTX_REQUEST_ID, payload.getAnalogMail().getRequestId());
        log.logStartingProcess(processName);
        HandlerContext context = new HandlerContext();

        MDCUtils.addMDCToContextAndExecute(Mono.just(payload)
                        .flatMap(singleStatusUpdate -> {

                            context.setPaperProgressStatusEvent(payload.getAnalogMail());
                            context.setEventId(messageId);
                            context.setDryRunEnabled(dryRunEnabled);

                            String statusCode = payload.getAnalogMail().getStatusCode();
                            String payloadProductType = payload.getAnalogMail().getProductType();
                            ProductType productType = resolveProductType(statusCode, payloadProductType);

                            return switch (productType){
                                case RS, ALL, RIS, _890 -> Mono.empty();
                                case AR -> handleEvent(statusCode, context, handlersFactoryAr);
                                case RIR -> handleEvent(statusCode, context, handlersFactoryRir);
                                case UNKNOWN -> handleUnrecognizedEventsHandler(context);
                            };
                        })
                        .doOnSuccess(resultFromAsync -> log.logEndingProcess(processName))
                )
                .onErrorResume(PnPaperTrackerValidationException.class, ex -> paperTrackerExceptionHandler.handleInternalException(ex, context.getMessageReceiveCount()))
                .block();
    }

    private ProductType resolveProductType(String statusCode, String payloadProductType) {
        String eventProductType = Optional.ofNullable(EventStatusCodeEnum.fromKey(statusCode))
                .map(e -> e.getProductType().getValue())
                .orElse("UNKNOWN");
        if(StringUtils.hasText(payloadProductType) && (eventProductType.equalsIgnoreCase(payloadProductType)
                || eventProductType.equalsIgnoreCase("ALL"))) {
            return ProductType.fromValue(payloadProductType);
        } else {
            return ProductType.fromValue(eventProductType);
        }
    }

    private Mono<Void> handleUnrecognizedEventsHandler(HandlerContext context) {
        return handlersFactoryGeneric.buildUnrecognizedEventsHandler(context);
    }

    /**
     * Gestisce gli eventi di un prodotto generico in base allo statusCode.
     * A seconda dello statusCode, invoca l'handler appropriato per gestire l'evento.
     *
     * @param statusCode lo statusCode dell'evento
     * @param context    il contesto dell'handler
     * @param factory    la factory specifica (RIR, AR, ecc.)
     */
    private Mono<Void> handleEvent(String statusCode, HandlerContext context, AbstractHandlersFactory factory) {
        EventStatusCodeEnum eventStatusCodeEnum = EventStatusCodeEnum.fromKey(statusCode);

        return switch (eventStatusCodeEnum.getCodeType()) {
            case INTERMEDIATE_EVENT -> {
                logStatusEvent("Intermediate", eventStatusCodeEnum, statusCode);
                yield factory.buildIntermediateEventsHandler(context);
            }
            case RETRYABLE_EVENT -> {
                logStatusEvent("Retryable", eventStatusCodeEnum, statusCode);
                yield factory.buildRetryEventHandler(context);
            }
            case NOT_RETRYABLE_EVENT -> {
                logStatusEvent("NotRetryable", eventStatusCodeEnum, statusCode);
                yield factory.buildNotRetryableEventHandler(context);
            }
            case FINAL_EVENT -> {
                logStatusEvent("Final", eventStatusCodeEnum, statusCode);
                yield factory.buildFinalEventsHandler(context);
            }
            case SAVE_ONLY_EVENT -> {
                logStatusEvent("SaveOnly", eventStatusCodeEnum, statusCode);
                yield factory.buildSaveOnlyEventHandler(context);
            }
        };
    }

    private void logStatusEvent(String statusEvent, EventStatusCodeEnum eventStatusCodeEnum, String statusCode) {
        log.info(HANDLING_EVENT_LOG,
                statusEvent,
                eventStatusCodeEnum.getProductType(),
                statusCode);
    }

}
