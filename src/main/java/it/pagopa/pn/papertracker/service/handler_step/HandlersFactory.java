package it.pagopa.pn.papertracker.service.handler_step;

import it.pagopa.pn.papertracker.model.HandlerContext;
import reactor.core.publisher.Mono;

public interface HandlersFactory {

    Handler buildFinalEventsHandler(HandlerContext context);
    Handler buildIntermediateEventsHandler(HandlerContext context);
    Handler buildRetryEventHandler(HandlerContext context);
    Handler buildNotRetryableEventHandler(HandlerContext context);
    Handler buildOcrResponseHandler(HandlerContext context);
    Handler buildSaveOnlyEventHandler(HandlerContext context);

}
