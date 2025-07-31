package it.pagopa.pn.papertracker.model;

import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PaperProgressStatusEvent;
import lombok.Data;

@Data
public class HandlerContext {

    private PaperProgressStatusEvent paperProgressStatusEvent;

}
