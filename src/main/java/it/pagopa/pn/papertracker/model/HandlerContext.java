package it.pagopa.pn.papertracker.model;

import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Event;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import lombok.Data;

import java.util.List;

@Data
public class HandlerContext {

    private PaperProgressStatusEvent paperProgressStatusEvent;
    private PaperTrackings paperTrackings;
    private List<Event> eventsToSend;

}
