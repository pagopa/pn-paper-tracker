package it.pagopa.pn.papertracker.middleware.msclient;

import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.DiscoveredAddress;
import reactor.core.publisher.Mono;

public interface DataVaultClient {

    Mono<String> anonymizeDiscoveredAddress(String trackingId, DiscoveredAddress discoveredAddress);
}
