package it.pagopa.pn.papertracker.middleware.queue.consumer.internal;

import it.pagopa.pn.commons.utils.MDCUtils;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.SingleStatusUpdate;
import it.pagopa.pn.papertracker.service.SourceQueueProxyService;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
@CustomLog
public class ExternalChannelSourceEventsHandler {
    private final SourceQueueProxyService sourceQueueProxyService;

    public void handleExternalChannelMessage(Message<SingleStatusUpdate> message) {
        SingleStatusUpdate payload = message.getPayload();
        if (Objects.isNull(payload) || Objects.isNull(payload.getAnalogMail())) {
            log.error("Received null payload or analogMail in ExternalChannelHandler");
            throw new IllegalArgumentException("Payload or analogMail cannot be null");
        }

        String processName = "processExternalChannelSourceMessage";
        MDC.put(MDCUtils.MDC_PN_CTX_REQUEST_ID, payload.getAnalogMail().getRequestId());
        log.logStartingProcess(processName);

        MDCUtils.addMDCToContextAndExecute(sourceQueueProxyService.handleExternalChannelMessage(message)
                        .doOnSuccess(unused -> log.logEndingProcess(processName)))
                .block();
    }
}
