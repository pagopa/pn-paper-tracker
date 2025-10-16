package it.pagopa.pn.papertracker.service.handler_step.generic;

import it.pagopa.pn.papertracker.exception.PaperTrackerException;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.model.EventTypeEnum;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.Handler;
import it.pagopa.pn.papertracker.service.handler_step.HandlerImpl;
import it.pagopa.pn.papertracker.service.handler_step.HandlersFactory;
import it.pagopa.pn.papertracker.service.handler_step._890.RECAG012AEventBuilder;
import it.pagopa.pn.papertracker.service.handler_step._890.RECAG012EventChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractHandlersFactory implements HandlersFactory {
    private final MetadataUpserter metadataUpserter;
    private final DeliveryPushSender deliveryPushSender;
    private final GenericFinalEventBuilder finalEventBuilder;
    private final IntermediateEventsBuilder intermediateEventsBuilder;
    private final DematValidator dematValidator;
    private final GenericSequenceValidator sequenceValidator;
    private final RetrySender retrySender;
    private final NotRetryableErrorInserting notRetryableErrorInserting;
    private final DuplicatedEventFiltering duplicatedEventFiltering;
    private final CheckTrackingState checkTrackingState;
    private final CheckOcrResponse checkOcrResponse;
    private final RetrySenderCON996 retrySenderCON996;
    private final RECAG012EventChecker recag012EventChecker;
    private final RECAG012AEventBuilder recag012AEventBuilder;

    public abstract ProductType getProductType();

    private final Map<EventTypeEnum, Function<HandlerContext, Handler>> dispatchers =
            new EnumMap<>(EventTypeEnum.class) {{
                put(EventTypeEnum.INTERMEDIATE_EVENT, AbstractHandlersFactory.this::buildIntermediateEventsHandler);
                put(EventTypeEnum.RETRYABLE_EVENT, AbstractHandlersFactory.this::buildRetryEventHandler);
                put(EventTypeEnum.NOT_RETRYABLE_EVENT, AbstractHandlersFactory.this::buildNotRetryableEventHandler);
                put(EventTypeEnum.FINAL_EVENT, AbstractHandlersFactory.this::buildFinalEventsHandler);
                put(EventTypeEnum.SAVE_ONLY_EVENT, AbstractHandlersFactory.this::buildSaveOnlyEventHandler);
                put(EventTypeEnum.OCR_RESPONSE_EVENT, AbstractHandlersFactory.this::buildOcrResponseHandler);
                put(EventTypeEnum.CON996_EVENT, AbstractHandlersFactory.this::buildCon996EventHandler);
                put(EventTypeEnum.STOCK_INTERMEDIATE_EVENT, AbstractHandlersFactory.this::buildStockIntermediateEventHandler);
                put(EventTypeEnum.RECAG012_EVENT, AbstractHandlersFactory.this::buildRecag012EventHandler);
            }};

    public Handler build(EventTypeEnum eventType, HandlerContext context) {
        log.info("Handling {} event for productType: [{}] (trackingId={})", eventType, getProductType(), context.getTrackingId());
        var handler = dispatchers.get(eventType);
        if (Objects.isNull(handler)) {
            log.error("No handler founded for EventType ={} and productType: [{}] (trackingId={})", eventType, getProductType(), context.getTrackingId());
            throw new PaperTrackerException(String.format("No handler founded for EventType =%s and productType: %s", eventType, getProductType()));
        }
        return handler.apply(context);
    }

    /**
     * Metodo che prende in carico gli eventi finali per poi inviare la risposta a delivery-push.
     * I step da compiere sono i seguenti:
     *  - Upsert metadati e demat
     *  - filtraggio eventi duplicati
     *  - Validazione triplette
     *  - Validazione demat tramite invio di un messaggio all'OCR
     *  - Costruzione evento finale
     *  - Invio evento finale a delivery-push
     *  - aggiornamento stato su PaperTrackings
     *
     * @param context   contesto in cui sono presenti tutti i dati necessari per il processo
     * @return Empty Mono se tutto è andato a buon fine, altrimenti un Mono Error
     */
    @Override
    public Handler buildFinalEventsHandler(HandlerContext context) {
        return new HandlerImpl(
                List.of(
                        metadataUpserter,
                        checkTrackingState,
                        sequenceValidator,
                        dematValidator,
                        finalEventBuilder,
                        deliveryPushSender
                ));
    }

    /**
     * Metodo che costruisce la lista di steps necessari al processamento di un evento intermedio.
     * I step da compiere sono i seguenti:
     *  - Upsert metadati e demat (se presenti)
     *  - filtraggio eventi duplicati
     *  - Costruzione eventi intermedi
     *  - Invio a pn-delivery-push
     *
     * @param context   contesto in cui sono presenti tutti i dati necessari per il processo
     * @return Empty Mono se tutto è andato a buon fine, altrimenti un Mono Error
     */
    @Override
    public Handler buildIntermediateEventsHandler(HandlerContext context) {
        return new HandlerImpl(
                List.of(
                        metadataUpserter,
                        checkTrackingState,
                        duplicatedEventFiltering,
                        intermediateEventsBuilder,
                        deliveryPushSender
                ));
    }

    /**
     * Metodo che costruisce la lista di steps necessari al processamento di un evento di retry.
     * I step da compiere sono i seguenti:
     *  - Upsert metadati e demat (se presenti)
     *  - filtraggio eventi duplicati
     *  - chiamata PaperChannel per richiedere il nuovo PCRETRY se esistente, e salva la nuova entità se esiste un nuovo PCRETRY
     *  altrimenti inserisce un errore nella tabella degli errori con category MAX_RETRY_REACHED_ERROR
     *  - costruzione dell'evento da inviare a pn-delivery-push
     *  - invio a pn-delivery-push
     *  - aggiornamento stato su PaperTrackings
     *
     * @param context   contesto in cui sono presenti tutti i dati necessari per il processo
     * @return Empty Mono se tutto è andato a buon fine, altrimenti un Mono Error
     */
    @Override
    public Handler buildRetryEventHandler(HandlerContext context) {
        return new HandlerImpl(
                List.of(
                        metadataUpserter,
                        checkTrackingState,
                        retrySender,
                        intermediateEventsBuilder,
                        deliveryPushSender
                ));
    }

    /**
     * Metodo che costruisce la lista di steps necessari al processamento di un evento notRetryable (CON998, CON997, CON995, CON993).
     * I step da compiere sono i seguenti:
     *  - Upsert metadati
     *  - filtraggio eventi duplicati
     *  - inserimento errore nella tabella PaperTrackingsError
     *  - costruzione dell'evento da inviare a pn-delivery-push
     *  - invio a pn-delivery-push
     *
     * @param context   contesto in cui sono presenti tutti i dati necessari per il processo
     * @return Empty Mono se tutto è andato a buon fine, altrimenti un Mono Error
     */
    @Override
    public Handler buildNotRetryableEventHandler(HandlerContext context) {
        return new HandlerImpl(
                List.of(
                        metadataUpserter,
                        checkTrackingState,
                        duplicatedEventFiltering,
                        notRetryableErrorInserting,
                        intermediateEventsBuilder,
                        deliveryPushSender
                ));
    }

    /**
     * Metodo che costruisce la lista di steps necessari al processamento di un evento di risposta della validazione ocr.
     * I step da compiere sono i seguenti:
     *  - Upsert metadati e demat (se presenti)
     *  - Costruzione evento finale
     *  - Invio a pn-delivery-push
     *  - aggiornamento stato su PaperTrackings
     *
     * @param context   contesto in cui sono presenti tutti i dati necessari per il processo
     * @return Empty Mono se tutto è andato a buon fine, altrimenti un Mono Error
     */
    @Override
    public Handler buildOcrResponseHandler(HandlerContext context) {
        return new HandlerImpl(
                List.of(
                        checkOcrResponse,
                        finalEventBuilder,
                        deliveryPushSender
                ));
    }

    @Override
    public Handler buildSaveOnlyEventHandler(HandlerContext context) {
        return new HandlerImpl(
                List.of(
                        metadataUpserter
                ));
    }

    /**
     * Metodo che costruisce la lista di steps necessari al processamento di un evento CON996.
     * I step da compiere sono i seguenti:
     *  - Upsert metadati
     *  - filtraggio eventi duplicati
     *  - chiamata PaperChannel per richiedere il nuovo PCRETRY se esistente, e salva la nuova entità se esiste un nuovo PCRETRY
     *  altrimenti inserisce un errore nella tabella degli errori con category NOT_RETRYABLE_EVENT_ERROR
     *  - costruzione dell'evento da inviare a pn-delivery-push
     *  - invio a pn-delivery-push
     *  - aggiornamento stato su PaperTrackings
     *
     * @param context   contesto in cui sono presenti tutti i dati necessari per il processo
     * @return Empty Mono se tutto è andato a buon fine, altrimenti un Mono Error
     */
    @Override
    public Handler buildCon996EventHandler(HandlerContext context) {
        return new HandlerImpl(
                List.of(
                        metadataUpserter,
                        checkTrackingState,
                        duplicatedEventFiltering,
                        retrySenderCON996,
                        intermediateEventsBuilder,
                        deliveryPushSender
                ));
    }

    @Override
    public Handler buildStockIntermediateEventHandler(HandlerContext context) {
        return new HandlerImpl(
                List.of(
                ));
    }

    @Override
    public Handler buildRecag012EventHandler(HandlerContext context) {
        return new HandlerImpl(
                List.of(
                ));
    }
}