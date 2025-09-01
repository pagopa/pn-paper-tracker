package it.pagopa.pn.papertracker.mapper;

import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.AttachmentDetails;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SendEvent;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.PaperTrackerOutput;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Attachment;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackerDryRunOutputs;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor(access = AccessLevel.NONE)
public class PaperTrackerDryRunOutputsMapper {

    public static PaperTrackerDryRunOutputs dtoToEntity(SendEvent event, String anonymizedDiscoveredAddressId) {
        PaperTrackerDryRunOutputs dryRunOutput = new PaperTrackerDryRunOutputs();
        dryRunOutput.setTrackingId(event.getRequestId());
        dryRunOutput.setCreated(Instant.now());
        dryRunOutput.setRegisteredLetterCode(event.getRegisteredLetterCode());
        dryRunOutput.setStatusDetail(event.getStatusDetail());
        dryRunOutput.setStatusDescription(event.getStatusDescription());
        dryRunOutput.setDeliveryFailureCause(event.getDeliveryFailureCause());
        dryRunOutput.setAnonymizedDiscoveredAddressId(anonymizedDiscoveredAddressId);

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

    public static PaperTrackerOutput toDtoPaperTrackerOutput(PaperTrackerDryRunOutputs entity) {

        PaperTrackerOutput dto = new PaperTrackerOutput();

        dto.setRegisteredLetterCode(entity.getRegisteredLetterCode());
        dto.setStatusCode(entity.getStatusCode());
        dto.setStatusDetail(entity.getStatusDetail());
        dto.setStatusDescription(entity.getStatusDescription());
        dto.setStatusDateTime(entity.getStatusDateTime());
        dto.setDeliveryFailureCause(entity.getDeliveryFailureCause());
        dto.setDiscoveredAddress(entity.getAnonymizedDiscoveredAddressId());
        dto.setClientRequestTimeStamp(entity.getClientRequestTimestamp());

        if (entity.getAttachments() != null) {
            List<it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.Attachment> attachmentsDto = getAttachments(entity);
            dto.setAttachments(attachmentsDto);
        }

        return dto;
    }

    private static List<it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.Attachment> getAttachments(PaperTrackerDryRunOutputs entity) {
        List<it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.Attachment> attachmentsDto = new ArrayList<>();
        for (it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Attachment att : entity.getAttachments()) {
            it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.Attachment attDto = new it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.Attachment();
            attDto.setId(att.getId());
            attDto.setDocumentType(att.getDocumentType());
            attDto.setUrl(att.getUri());
            attDto.setDate(att.getDate());
            attachmentsDto.add(attDto);
        }
        return attachmentsDto;
    }

    private static Attachment buildAttachmentForExternalChannelOutputEvent(AttachmentDetails attachment) {
        Attachment attachmentEntity = new Attachment();
        attachmentEntity.setId(attachment.getId());
        attachmentEntity.setDate(attachment.getDate().toInstant());
        attachmentEntity.setDocumentType(attachment.getDocumentType());
        attachmentEntity.setUri(attachment.getUri());
        return attachmentEntity;
    }
}
