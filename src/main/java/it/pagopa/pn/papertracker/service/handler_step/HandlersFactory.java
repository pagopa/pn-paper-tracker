package it.pagopa.pn.papertracker.service.handler_step;

import it.pagopa.pn.papertracker.model.HandlerContext;
import reactor.core.publisher.Mono;

public interface HandlersFactory {

    Mono<Void> buildFinalEventsHandler(HandlerContext context);
    Mono<Void> buildIntermediateEventsHandler(HandlerContext context);
    Mono<Void> buildRetryEventHandler(HandlerContext context);
    Mono<Void> buildNotRetryableEventHandler(HandlerContext context);
    Mono<Void> buildOcrResponseHandler(HandlerContext context);
    Mono<Void> buildUnrecognizedEventsHandler(HandlerContext context);
    Mono<Void> buildSaveOnlyEventHandler(HandlerContext context);

}
