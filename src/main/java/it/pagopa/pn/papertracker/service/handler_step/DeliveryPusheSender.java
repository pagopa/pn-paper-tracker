package it.pagopa.pn.papertracker.service.handler_step;

import it.pagopa.pn.papertracker.model.HandlerContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class DeliveryPusheSender implements HandlerStep {

    @Override
    public Mono<Void> execute(HandlerContext context) {
        return Mono.empty();
    }
}