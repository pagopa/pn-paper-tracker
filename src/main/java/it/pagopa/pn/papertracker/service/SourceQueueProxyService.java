package it.pagopa.pn.papertracker.service;

import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.SingleStatusUpdate;
import org.springframework.messaging.Message;
import reactor.core.publisher.Mono;

public interface SourceQueueProxyService {

    Mono<Void> handleExternalChannelMessage(Message<SingleStatusUpdate> message);

}
