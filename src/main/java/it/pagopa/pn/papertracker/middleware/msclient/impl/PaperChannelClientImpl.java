package it.pagopa.pn.papertracker.middleware.msclient.impl;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerNotFoundException;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.api.PcRetryApi;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PcRetryResponse;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
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

    public static final String RETRY = ".PCRETRY_";
    public static final String PCRETRY = "PCRETRY_";

    private final PcRetryApi pcRetryApi;
    private final PnPaperTrackerConfigs config;

    @Override
    public Mono<PcRetryResponse> getPcRetry(PaperTrackings paperTrackings) {
        if(config.getEnableRetrySendEngageFor().contains(paperTrackings.getProductType())){
            return getPcRetryPaperChannel(paperTrackings.getTrackingId());
        }
        log.debug("giving mock response...");
        return getPcRetryPaperMock(paperTrackings);
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

    private Mono<PcRetryResponse> getPcRetryPaperMock(PaperTrackings paperTrackings) {
        PcRetryResponse pcRetryResponse = new PcRetryResponse();
        pcRetryResponse.setParentRequestId(paperTrackings.getTrackingId());
        pcRetryResponse.setDeliveryDriverId(paperTrackings.getUnifiedDeliveryDriver());

        if (hasOtherAttempt(paperTrackings.getTrackingId())) {
            pcRetryResponse.setRetryFound(true);
            String newRequestId = setRetryRequestId(paperTrackings.getTrackingId());
            setRetryRequestIdAndPcRetry(pcRetryResponse, newRequestId);
        }

        pcRetryResponse.setRetryFound(false);
        return Mono.just(pcRetryResponse);
    }

    public boolean hasOtherAttempt(String requestId) {
        return config.getMaxPcRetryMock() == -1 || config.getMaxPcRetryMock() >= getRetryAttempt(requestId);
    }

    private int getRetryAttempt(String requestId) {
        int retry = 0;
        if (requestId.contains(RETRY)) {
            retry = Integer.parseInt(requestId.substring(requestId.lastIndexOf("_")+1));
        }
        return retry;
    }

    public String setRetryRequestId(String requestId) {
        if (requestId.contains(RETRY)) {
            String prefix = requestId.substring(0, requestId.indexOf(RETRY));
            String attempt = String.valueOf(getRetryAttempt(requestId) + 1);
            requestId = prefix.concat(RETRY).concat(attempt);
        }
        return requestId;
    }

    private void setRetryRequestIdAndPcRetry(PcRetryResponse pcRetryResponse, String newRequestId) {
        String suffix = newRequestId.substring(newRequestId.indexOf(PCRETRY), newRequestId.length());
        pcRetryResponse.setPcRetry(suffix);
        pcRetryResponse.setRequestId(newRequestId);
    }
}
