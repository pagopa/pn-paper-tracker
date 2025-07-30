package it.pagopa.pn.papertracker.mapper;

import it.pagopa.pn.papertracker.config.StatusCodeConfiguration;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Event;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackerDryRunOutputs;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;

import java.time.Instant;

public class PaperTrackerDryRunOutputsMapper {

    public static PaperTrackerDryRunOutputs buildDryRunOutput(Event event, PaperTrackings paperTrackings) {
        PaperTrackerDryRunOutputs dryRunOutput = new PaperTrackerDryRunOutputs();
        dryRunOutput.setRequestId(paperTrackings.getRequestId());
        dryRunOutput.setCreated(Instant.now());
        dryRunOutput.setRegisteredLetterCode(paperTrackings.getNotificationState().getRegisteredLetterCode());
        dryRunOutput.setStatusCode(event.getStatusCode());
        dryRunOutput.setStatusDetail(event.getEventStatus().name());
        dryRunOutput.setStatusDescription(StatusCodeConfiguration
                .StatusCodeConfigurationEnum.fromKey(event.getStatusCode())
                .getStatusCodeDescription());
        dryRunOutput.setStatusDateTime(event.getStatusTimestamp().toString());
        dryRunOutput.setDeliveryFailureCause(event.getDeliveryFailureCause());
        dryRunOutput.setAttachments(event.getAttachments());
        dryRunOutput.setDiscoveredAddress(paperTrackings.getNotificationState().getDiscoveredAddress());
        dryRunOutput.setClientRequestTimestamp(event.getRequestTimestamp().toString());

        return dryRunOutput;
    }

}
