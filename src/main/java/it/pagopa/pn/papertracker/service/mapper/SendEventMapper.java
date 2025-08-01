package it.pagopa.pn.papertracker.service.mapper;

import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SendEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.StatusCodeEnum;

import java.time.OffsetDateTime;

public class SendEventMapper {

    private SendEventMapper() {
    }

    public static SendEvent toSendEvent(PaperProgressStatusEvent paperProgressStatusEvent, StatusCodeEnum statusCode, String statusDetail, OffsetDateTime statusDateTime) {
        SendEvent sendEvent = new SendEvent();
        sendEvent.setStatusCode(statusCode);
        sendEvent.setStatusDetail(statusDetail);
        sendEvent.setStatusDescription(paperProgressStatusEvent.getStatusDescription());
        sendEvent.setRequestId(paperProgressStatusEvent.getRequestId());
        sendEvent.setStatusDateTime(statusDateTime);
        sendEvent.setRegisteredLetterCode(paperProgressStatusEvent.getRegisteredLetterCode());
        sendEvent.setClientRequestTimeStamp(paperProgressStatusEvent.getClientRequestTimeStamp());
        sendEvent.setDeliveryFailureCause(paperProgressStatusEvent.getDeliveryFailureCause());
        sendEvent.setDiscoveredAddress(null); //TODO

        if (paperProgressStatusEvent.getAttachments() != null && !paperProgressStatusEvent.getAttachments().isEmpty()) {
            sendEvent.setAttachments(paperProgressStatusEvent.getAttachments());
        }
        return sendEvent;
    }

}
