package it.pagopa.pn.papertracker.service;

import it.pagopa.pn.papertracker.middleware.queue.model.ExternalChannelEvent;
import it.pagopa.pn.papertracker.middleware.queue.model.ExternalChannelEventHeader;
import reactor.core.publisher.Mono;

public interface SourceQueueProxyService {

    Mono<Void> handleExternalChannelMessage(ExternalChannelEvent event, ExternalChannelEventHeader header);

}
