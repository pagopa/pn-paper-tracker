package it.pagopa.pn.papertracker.middleware.queue.model;

import it.pagopa.pn.api.dto.events.GenericEvent;
import it.pagopa.pn.api.dto.events.GenericEventHeader;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PaperChannelUpdate;
import lombok.AllArgsConstructor;
import lombok.Builder;

@AllArgsConstructor
@Builder
public class DeliveryPushEvent implements GenericEvent<GenericEventHeader, PaperChannelUpdate> {

    private GenericEventHeader header;
    private PaperChannelUpdate payload;

    @Override
    public GenericEventHeader getHeader() {
        return header;
    }

    @Override
    public PaperChannelUpdate getPayload() {
        return payload;
    }
}
