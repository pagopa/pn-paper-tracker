package it.pagopa.pn.papertracker.mapper;

import it.pagopa.pn.api.dto.events.GenericEventHeader;
import it.pagopa.pn.papertracker.config.StatusCodeConfiguration;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.AttachmentDetails;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SingleStatusUpdate;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Attachment;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Event;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.queue.model.ExternalChannelOutputEvent;
import it.pagopa.pn.papertracker.model.EventStatus;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

public class ExternalChannelOutputEventMapper {

    public static ExternalChannelOutputEvent buildExternalChannelOutputEvent(Event event, PaperTrackings paperTrackings) {
        return ExternalChannelOutputEvent.builder()
                .header( GenericEventHeader.builder()
                        .publisher("pn-paper-tracking")
                        .eventId(UUID.randomUUID().toString())
                        .createdAt( Instant.now() )
                        .eventType("")
                        .build())
                .payload( SingleStatusUpdate.builder()
                        .analogMail(
                                //TODO aggiungere discoered
                                PaperProgressStatusEvent.builder()
                                    .requestId(paperTrackings.getRequestId())
                                    .attachments(event.getAttachments().stream()
                                            .map(ExternalChannelOutputEventMapper::buildAttachmentForExternalChannelOutputEvent)
                                            .toList())
                                    .registeredLetterCode(paperTrackings.getNotificationState().getRegisteredLetterCode())
                                    .deliveryFailureCause(event.getDeliveryFailureCause())
                                    .statusDateTime(OffsetDateTime.from(event.getStatusTimestamp()))
                                    .clientRequestTimeStamp(OffsetDateTime.from(event.getRequestTimestamp()))
                                    .statusCode(EventStatus.valueOf(event.getStatusCode()).name())
                                    .statusDescription(StatusCodeConfiguration
                                            .StatusCodeConfigurationEnum.fromKey(event.getStatusCode())
                                            .getStatusCodeDescription())
                                        .build())
                        .build())
                .build();
    }

    public static AttachmentDetails buildAttachmentForExternalChannelOutputEvent(Attachment attachment) {
        return AttachmentDetails.builder()
                .date(OffsetDateTime.from(attachment.getDate()))
                .id(attachment.getId())
                .documentType(attachment.getDocumentType())
                .uri(attachment.getUrl())
                .build();
    }
}
