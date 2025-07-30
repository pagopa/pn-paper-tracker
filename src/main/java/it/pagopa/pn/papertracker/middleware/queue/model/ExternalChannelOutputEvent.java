package it.pagopa.pn.papertracker.middleware.queue.model;

import it.pagopa.pn.api.dto.events.GenericEvent;
import it.pagopa.pn.api.dto.events.GenericEventHeader;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SingleStatusUpdate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder(toBuilder = true)
public class ExternalChannelOutputEvent implements GenericEvent<GenericEventHeader, SingleStatusUpdate> {

    private GenericEventHeader header;
    private SingleStatusUpdate payload;
}
