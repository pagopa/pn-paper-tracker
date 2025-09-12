package it.pagopa.pn.papertracker.service.handler_step.generic;

import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import it.pagopa.pn.papertracker.service.handler_step.HandlersFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

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
    private final StateUpdater stateUpdater;
    private final CheckTrackingState checkTrackingState;

    /**
     * Metodo che data una lista di HandlerStep esegue ogni step, passando il contex per eventuali modifiche ai dati
     *
     * @param steps   HandlerStep da eseguire
     * @param context HandlerContext che contiene i dati per i processi
     * @return Mono Void se tutto è andato a buon fine, altrimenti Mono Error
     */
    public Mono<Void> buildEventsHandler(List<HandlerStep> steps, HandlerContext context) {
        return Flux.fromIterable(steps)
                .concatMap(step -> {
                    if (context.isStopExecution()) {
                        log.debug("Requested stop execution for trackingId: {} on step: {}", context.getTrackingId(), step.getClass().getSimpleName());
                        return Mono.empty();
                    }
                    return step.execute(context);
                })
                .then();
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
    public Mono<Void> buildFinalEventsHandler(HandlerContext context) {
        return buildEventsHandler(
                List.of(
                        metadataUpserter,
                        checkTrackingState,
                        sequenceValidator,
                        dematValidator,
                        finalEventBuilder,
                        deliveryPushSender,
                        stateUpdater
                ), context);
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
    public Mono<Void> buildIntermediateEventsHandler(HandlerContext context) {
        return buildEventsHandler(
                List.of(
                        metadataUpserter,
                        checkTrackingState,
                        duplicatedEventFiltering,
                        intermediateEventsBuilder,
                        deliveryPushSender
                ), context);
    }

    /**
     * Metodo che costruisce la lista di steps necessari al processamento di un evento di retry.
     * I step da compiere sono i seguenti:
     *  - Upsert metadati e demat (se presenti)
     *  - filtraggio eventi duplicati
     *  - chiamata PaperChannel per richiedere il nuovo PCRETRY se esistente, e salva la nuova entità se esiste un nuovo PCRETRY
     *  - costruzione dell'evento da inviare a pn-delivery-push
     *  - invio a pn-delivery-push
     *  - aggiornamento stato su PaperTrackings
     *
     * @param context   contesto in cui sono presenti tutti i dati necessari per il processo
     * @return Empty Mono se tutto è andato a buon fine, altrimenti un Mono Error
     */
    @Override
    public Mono<Void> buildRetryEventHandler(HandlerContext context) {
        return buildEventsHandler(
                List.of(
                        metadataUpserter,
                        checkTrackingState,
                        retrySender,
                        intermediateEventsBuilder,
                        deliveryPushSender,
                        stateUpdater
                ), context);
    }

    /**
     * Metodo che costruisce la lista di steps necessari al processamento di un evento notRetryable (CON998, CON997, CON996, CON995, CON993).
     * I step da compiere sono i seguenti:
     *  - Upsert metadati
     *  - filtraggio eventi duplicati
     *  - inserimento errore nella tabella PaperTrackingsError
     *  - costruzione dell'evento da inviare a pn-delivery-push
     *  - invio a pn-delivery-push
     *  - aggiornamento stato su PaperTrackings
     *
     * @param context   contesto in cui sono presenti tutti i dati necessari per il processo
     * @return Empty Mono se tutto è andato a buon fine, altrimenti un Mono Error
     */
    @Override
    public Mono<Void> buildNotRetryableEventHandler(HandlerContext context) {
        return buildEventsHandler(
                List.of(
                        metadataUpserter,
                        checkTrackingState,
                        duplicatedEventFiltering,
                        notRetryableErrorInserting,
                        intermediateEventsBuilder,
                        deliveryPushSender
                ), context);
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
    public Mono<Void> buildOcrResponseHandler(HandlerContext context) {
        return buildEventsHandler(
                List.of(
                        finalEventBuilder,
                        deliveryPushSender,
                        stateUpdater
                ), context);
    }

    @Override
    public Mono<Void> buildUnrecognizedEventsHandler(HandlerContext context) {
        return buildEventsHandler(
                List.of(
                        metadataUpserter
                ), context);
    }
}