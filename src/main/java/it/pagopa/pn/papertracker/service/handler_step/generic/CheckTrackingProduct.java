package it.pagopa.pn.papertracker.service.handler_step.generic;

import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.mapper.PaperTrackingsErrorsMapper;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.model.EventStatusCodeEnum;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;


@Component
@Slf4j
public class CheckTrackingProduct implements HandlerStep {

    /**
     * Step che effettua un controllo di coerenza tra il tipo di prodotto associato al tracking e quello atteso per lo statusCode dell'evento ricevuto &&
     * quello contenuto nel payload.
     * Se il tipo di prodotto non corrisponde, viene generato un errore di validazione.
     * @param context Contesto contenente le informazioni necessarie per l'elaborazione dell'evento.
     * @return Mono<Void>
     */

    @Override
    public Mono<Void> execute(HandlerContext context) {
        String paperTrackingProduct = context.getPaperTrackings().getProductType();
        String payloadProduct = context.getPaperProgressStatusEvent().getProductType();
        String statusCode = context.getPaperProgressStatusEvent().getStatusCode();
        String productType = EventStatusCodeEnum.fromKey(statusCode).getProductType().getValue();
        if ((productType.equals(ProductType.ALL.getValue()) || paperTrackingProduct.equals(productType)) && paperTrackingProduct.equals(payloadProduct)) {
            return Mono.empty();
        }

        String errorMsg = String.format("Product type mismatch for trackingId %s: expected %s, but got %s", context.getTrackingId(), paperTrackingProduct, productType);
        Map<String, Object> additionalDetails = Map.of("statusCode", statusCode,
                "statusTimestamp", Objects.toString(context.getPaperProgressStatusEvent().getStatusDateTime(), null)
        );
        return Mono.error(new PnPaperTrackerValidationException(
                errorMsg,
                PaperTrackingsErrorsMapper.buildPaperTrackingsError(
                        context.getPaperTrackings(),
                        context.getPaperProgressStatusEvent().getStatusCode(),
                        ErrorCategory.INCONSISTENT_STATE,
                        ErrorCause.VALUES_NOT_MATCHING,
                        errorMsg,
                        additionalDetails,
                        FlowThrow.SEQUENCE_VALIDATION,
                        ErrorType.ERROR,
                        context.getEventId()
                )));
    }
}