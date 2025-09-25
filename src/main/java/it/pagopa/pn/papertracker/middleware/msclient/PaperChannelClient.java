package it.pagopa.pn.papertracker.middleware.msclient;

import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PcRetryResponse;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import reactor.core.publisher.Mono;

public interface PaperChannelClient {

    Mono<PcRetryResponse> getPcRetry(PaperTrackings paperTrackings, Boolean checkApplyRasterization);
}
