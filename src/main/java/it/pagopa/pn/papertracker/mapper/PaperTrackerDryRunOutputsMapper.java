package it.pagopa.pn.papertracker.mapper;

import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.AttachmentDetails;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SendEvent;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackerDryRunOutputs;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.Objects;

public class PaperTrackerDryRunOutputsMapper {

    public static PaperTrackerDryRunOutputs dtoToEntity(SendEvent event, String discoveredAddress) {
        PaperTrackerDryRunOutputs dryRunOutput = new PaperTrackerDryRunOutputs();
        dryRunOutput.setRequestId(event.getRequestId());
        dryRunOutput.setCreated(Instant.now());
        dryRunOutput.setRegisteredLetterCode(event.getRegisteredLetterCode());
        dryRunOutput.setStatusDetail(event.getStatusDetail());
        dryRunOutput.setStatusDescription(event.getStatusDescription());
        dryRunOutput.setDeliveryFailureCause(event.getDeliveryFailureCause());
        dryRunOutput.setDiscoveredAddress(discoveredAddress);

        if(Objects.nonNull(event.getStatusCode())) {
            dryRunOutput.setStatusCode(event.getStatusCode().name());
        }

        if(Objects.nonNull(event.getStatusDateTime())) {
            dryRunOutput.setStatusDateTime(event.getStatusDateTime().toString());
        }

        if(!CollectionUtils.isEmpty(event.getAttachments())) {
            dryRunOutput.setAttachments((event.getAttachments().stream()
                    .map(PaperTrackerDryRunOutputsMapper::buildAttachmentForExternalChannelOutputEvent)
                    .toList()));
        }

        if(Objects.nonNull(event.getClientRequestTimeStamp())) {
            dryRunOutput.setClientRequestTimestamp(event.getClientRequestTimeStamp().toString());
        }

        return dryRunOutput;
    }

    private static it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Attachment buildAttachmentForExternalChannelOutputEvent(AttachmentDetails attachment) {
        it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Attachment attachmentEntity = new it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Attachment();
        attachmentEntity.setId(attachment.getId());
        attachmentEntity.setDate(attachment.getDate().toInstant());
        attachmentEntity.setDocumentType(attachment.getDocumentType());
        attachmentEntity.setUrl(attachment.getUri());
        return attachmentEntity;
    }
}
