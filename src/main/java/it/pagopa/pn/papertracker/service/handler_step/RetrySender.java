package it.pagopa.pn.papertracker.service.handler_step;

import it.pagopa.pn.papertracker.model.HandlerContext;
import reactor.core.publisher.Mono;

public class RetrySender implements HandlerStep {

    @Override
    public Mono<Void> execute(HandlerContext context) {
        return Mono.empty();
    }
}