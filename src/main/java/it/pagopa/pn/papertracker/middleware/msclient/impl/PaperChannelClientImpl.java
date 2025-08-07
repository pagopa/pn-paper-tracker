package it.pagopa.pn.papertracker.middleware.msclient.impl;

import it.pagopa.pn.papertracker.exception.PnPaperTrackerNotFoundException;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.api.PcRetryApi;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PcRetryResponse;
import it.pagopa.pn.papertracker.middleware.msclient.PaperChannelClient;
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

    @Override
    public Mono<PcRetryResponse> getPcRetry(String trackingId) {
        return pcRetryApi.getPcRetry(trackingId)
                .onErrorResume(throwable -> {
                    if (throwable instanceof WebClientResponseException webEx && webEx.getStatusCode() == HttpStatus.NOT_FOUND) {
                        return Mono.error(new PnPaperTrackerNotFoundException("RequestId not found", webEx.getMessage()));
                    }
                    return Mono.error(throwable);
                });
    }
}
