package it.pagopa.pn.papertracker.service.handler_step.AR;

import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import it.pagopa.pn.papertracker.service.handler_step.HandlersFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class HandlersFactoryAr implements HandlersFactory {

    @Override
    public Mono<Void> buildEventsHandler(List<HandlerStep> steps, HandlerContext context) {
        return Flux.fromIterable(steps)
                //TODO srs concatMap ma non può essere utilizzata su Flux
                .flatMap(step -> step.execute(context))
                .then();
    };

    @Override
    public Mono<Void> buildFinalEventsHandler(HandlerContext context) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> buildIntermediateEventsHandler(HandlerContext context) {
        return Mono.empty();
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
