package it.pagopa.pn.papertracker.model;

import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SendEvent;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class HandlerContext {

    private PaperProgressStatusEvent paperProgressStatusEvent;
    private PaperTrackings paperTrackings;
    private String anonymizedDiscoveredAddressId;
    private List<SendEvent> eventsToSend = new ArrayList<>();
    private boolean stopExecution = false;

}
