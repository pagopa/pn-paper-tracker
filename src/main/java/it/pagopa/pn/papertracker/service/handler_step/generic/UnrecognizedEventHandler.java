package it.pagopa.pn.papertracker.service.handler_step.generic;

import it.pagopa.pn.papertracker.model.HandlerContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class UnrecognizedEventHandler{

    private final MetadataUpserter metadataUpserter;

    public Mono<Void> handle(HandlerContext ctx) {
        return Mono.just(metadataUpserter)
                .map(s -> s.execute(ctx))
                .then();
    }
}

