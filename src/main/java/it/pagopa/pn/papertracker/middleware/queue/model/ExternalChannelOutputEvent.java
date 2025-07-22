package it.pagopa.pn.papertracker.middleware.queue.model;

import com.sngular.apigenerator.asyncapi.business_model.model.event.ExternalChannelOutputsPayload;
import it.pagopa.pn.api.dto.events.GenericEvent;
import it.pagopa.pn.api.dto.events.GenericEventHeader;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class ExternalChannelOutputEvent implements GenericEvent<GenericEventHeader, ExternalChannelOutputsPayload> {

    private GenericEventHeader header;
    private ExternalChannelOutputsPayload payload;

    @Override
    public GenericEventHeader getHeader() {
        return header;
    }

    @Override
    public ExternalChannelOutputsPayload getPayload() {
        return payload;
    }
}
