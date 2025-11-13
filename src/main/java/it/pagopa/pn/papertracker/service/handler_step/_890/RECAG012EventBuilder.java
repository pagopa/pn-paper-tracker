package it.pagopa.pn.papertracker.service.handler_step._890;

import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class RECAG012EventBuilder implements HandlerStep {

    @Override
    public Mono<Void> execute(HandlerContext context) {
        return Mono.empty();
    }
}
