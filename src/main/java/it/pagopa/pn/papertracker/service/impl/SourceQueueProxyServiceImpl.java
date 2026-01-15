package it.pagopa.pn.papertracker.service.impl;

import it.pagopa.pn.papertracker.middleware.queue.model.ExternalChannelEvent;
import it.pagopa.pn.papertracker.middleware.queue.model.ExternalChannelEventHeader;
import it.pagopa.pn.papertracker.middleware.queue.producer.ExternalChannelToPaperChannelMomProducer;
import reactor.core.publisher.Mono;

public class SourceQueueProxyServiceImpl {

    ExternalChannelToPaperChannelMomProducer paperChannelProducer;
    public Mono<Void> handleExternalChannelMessage(ExternalChannelEvent event, ExternalChannelEventHeader header) {

        return Mono.empty();
    }
}
