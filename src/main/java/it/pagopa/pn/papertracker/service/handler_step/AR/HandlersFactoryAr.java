package it.pagopa.pn.papertracker.service.handler_step.AR;

import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.DeliveryPushSender;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import it.pagopa.pn.papertracker.service.handler_step.HandlersFactory;
import it.pagopa.pn.papertracker.service.handler_step.MetadataUpserter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
public class HandlersFactoryAr implements HandlersFactory {
    private final MetadataUpserter metadataUpserter;
    private final DeliveryPushSender deliveryPushSender;

    @Override
    public Mono<Void> buildEventsHandler(List<HandlerStep> steps, HandlerContext context) {
        return Flux.fromIterable(steps)
                .concatMap(step -> step.execute(context))
                .then();
    };

    @Override
    public Mono<Void> buildFinalEventsHandler(HandlerContext context) {
        return Mono.empty();
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
                        //TODO aggiungere costruzione evento/i intermedi
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
}
