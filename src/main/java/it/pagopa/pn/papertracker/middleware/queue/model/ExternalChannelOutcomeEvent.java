package it.pagopa.pn.papertracker.middleware.queue.model;

import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SingleStatusUpdate;
import lombok.Data;

@Data
public class ExternalChannelOutcomeEvent {
    private String version;
    private String id;
    private String detailType;
    private String source;
    private SingleStatusUpdate detail;
}