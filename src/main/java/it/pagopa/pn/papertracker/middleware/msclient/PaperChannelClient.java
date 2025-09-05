package it.pagopa.pn.papertracker.middleware.msclient;

import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PcRetryResponse;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import reactor.core.publisher.Mono;

public interface PaperChannelClient {

    Mono<PcRetryResponse> getPcRetry(String trackingId, ProductType productType);
}
