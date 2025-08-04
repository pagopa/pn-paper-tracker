package it.pagopa.pn.papertracker.service.handler_step.AR;

import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class HandlersFactoryAr implements HandlersFactory {
    private final MetadataUpserter metadataUpserter;
    private final DeliveryPushSender deliveryPushSender;
    private final FinalEventBuilder finalEventBuilder;
    private final IntermediateEventsBuilder intermediateEventsBuilder;
    private final DematValidator dematValidator;
    private final SequenceValidator sequenceValidator;

    /**
     * Metodo che data una lista di HandlerStep esegue ogni step, passando il contex per eventuali modifiche ai dati
     *
     * @param steps     HandlerStep da eseguire
     * @param context   HandlerContext che contiene i dati per i processi
     * @return Mono Void se tutto è andato a buon fine, altrimenti Mono Error
     */
    @Override
    public Mono<Void> buildEventsHandler(List<HandlerStep> steps, HandlerContext context) {
        return Flux.fromIterable(steps)
                .concatMap(step -> {
                    if (context.isStopExecution()) {
                        log.debug("Interruzione esecuzione richiesta - saltando step: {}", step.getClass().getSimpleName());
                        return Mono.empty();
                    }
                    return step.execute(context);
                })
                .then();
    };

    /**
     * Metodo che prende in carico gli eventi finali per poi inviare la risposta a delivery-push.
     * I step da compiere sono i seguenti:
     *  - Upsert metadati e demat
     *  - Validazione triplette
     *  - Validazione demat tramite invio di un messaggio all'OCR
     *  - Costruzione evento finale
     *  - Invio evento finale a delivery-push
     *
     * @param context   contesto in cui sono presenti tutti i dati necessari per il processo
     * @return Empty Mono se tutto è andato a buon fine, altrimenti un Mono Error
     */
    @Override
    public Mono<Void> buildFinalEventsHandler(HandlerContext context) {
        return buildEventsHandler(
                List.of(
                        metadataUpserter,
                        sequenceValidator,
                        dematValidator,
                        finalEventBuilder,
                        deliveryPushSender
                ), context);
    }

    /**
     * Metodo che costruisce la lista di steps necessari al processamento di un evento intermedio.
     * I step da compiere sono i seguenti:
     *  - Upsert metadati e demat (se presenti)
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
                        intermediateEventsBuilder,
                        deliveryPushSender
                ), context);
    }

    @Override
    public Mono<Void> buildRetryEventHandler(HandlerContext context) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> buildOcrResponseHandler(HandlerContext context) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> buildUnrecognizedEventsHandler(HandlerContext context) {
        return buildEventsHandler(
                List.of(
                        metadataUpserter
                ), context);
    }
}
