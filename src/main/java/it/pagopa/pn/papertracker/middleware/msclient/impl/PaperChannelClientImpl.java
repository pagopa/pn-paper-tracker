package it.pagopa.pn.papertracker.middleware.msclient.impl;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerNotFoundException;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.api.PcRetryApi;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PcRetryResponse;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.msclient.PaperChannelClient;
import it.pagopa.pn.papertracker.utils.PcRetryUtilsMock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaperChannelClientImpl implements PaperChannelClient {

    private final PcRetryApi pcRetryApi;
    private final PnPaperTrackerConfigs config;

    @Override
    public Mono<PcRetryResponse> getPcRetry(PaperTrackings paperTrackings) {
        if(config.getEnableRetrySendEngageFor().contains(paperTrackings.getProductType())){
            return getPcRetryPaperChannel(paperTrackings.getTrackingId());
        }
        log.debug("giving mock response...");
        return PcRetryUtilsMock.getPcRetryPaperMock(paperTrackings, config.getMaxPcRetryMock());
    }

    private Mono<PcRetryResponse> getPcRetryPaperChannel(String trackingId) {
        return pcRetryApi.getPcRetry(trackingId)
                .onErrorResume(throwable -> {
                    if (throwable instanceof WebClientResponseException webEx && webEx.getStatusCode() == HttpStatus.NOT_FOUND) {
                        return Mono.error(new PnPaperTrackerNotFoundException("RequestId not found", webEx.getMessage()));
                    }
                    return Mono.error(throwable);
                });
    }

}
