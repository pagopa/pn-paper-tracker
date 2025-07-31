package it.pagopa.pn.papertracker.model;

import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SendEvent;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
public class HandlerContext {

    private PaperProgressStatusEvent paperProgressStatusEvent;
    private List<SendEvent> eventsToSend;
    private PaperTrackings paperTrackings;

}
