package it.pagopa.pn.papertracker.service.handler_step;

import it.pagopa.pn.papertracker.model.HandlerContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.List;

@Slf4j
public abstract class AbstractHandlersFactory{

    /**
     * Metodo che data una lista di HandlerStep esegue ogni step, passando il contex per eventuali modifiche ai dati
     *
     * @param steps     HandlerStep da eseguire
     * @param context   HandlerContext che contiene i dati per i processi
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
    };

}
