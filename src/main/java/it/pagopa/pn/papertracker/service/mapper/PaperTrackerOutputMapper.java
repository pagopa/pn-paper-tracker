package it.pagopa.pn.papertracker.service.mapper;

import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.Attachment;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackerDryRunOutputs;

import java.util.ArrayList;
import java.util.List;

public class PaperTrackerOutputMapper {

    private PaperTrackerOutputMapper() {
    }

    public static it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.PaperTrackerOutput toDtoPaperTrackerOutput(
            it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackerDryRunOutputs entity) {

        if (entity == null) {
            return null;
        }

        it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.PaperTrackerOutput dto =
                new it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.PaperTrackerOutput();

        dto.setRegisteredLetterCode(entity.getRegisteredLetterCode());
        dto.setStatusCode(entity.getStatusCode());
        dto.setStatusDetail(entity.getStatusDetail());
        dto.setStatusDescription(entity.getStatusDescription());
        dto.setStatusDateTime(entity.getStatusDateTime());
        dto.setDeliveryFailureCause(entity.getDeliveryFailureCause());
        dto.setDiscoveredAddress(entity.getAnonymizedDiscoveredAddressId());
        dto.setClientRequestTimeStamp(entity.getClientRequestTimestamp());

        if (entity.getAttachments() != null) {
            List<Attachment> attachmentsDto = getAttachments(entity);
            dto.setAttachments(attachmentsDto);
        }

        return dto;
    }

    private static List<Attachment> getAttachments(PaperTrackerDryRunOutputs entity) {
        List<Attachment> attachmentsDto = new ArrayList<>();
        for (it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Attachment att : entity.getAttachments()) {
            Attachment attDto =
                    new Attachment();
            attDto.setId(att.getId());
            attDto.setDocumentType(att.getDocumentType());
            attDto.setUrl(att.getUri());
            attDto.setDate(att.getDate() != null ? att.getDate().toString() : null);
            attachmentsDto.add(attDto);
        }
        return attachmentsDto;
    }
}
