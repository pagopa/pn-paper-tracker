package it.pagopa.pn.papertracker.mapper;

import com.sngular.apigenerator.asyncapi.business_model.model.event.ExternalChannelOutputsPayload;
import it.pagopa.pn.api.dto.events.GenericEventHeader;
import it.pagopa.pn.papertracker.config.StatusCodeConfiguration;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Attachment;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Event;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.queue.model.ExternalChannelOutputEvent;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
                .payload( ExternalChannelOutputsPayload.builder()
                        .requestId(paperTrackings.getRequestId())
                        .attachments(event.getAttachments().stream()
                                .map(ExternalChannelOutputEventMapper::buildAttachmentForExternalChannelOutputEvent)
                                .toList())
                        .created(LocalDateTime.from(Instant.now().atZone(ZoneId.systemDefault())))
                        .registeredLetterCode(paperTrackings.getNotificationState().getRegisteredLetterCode())
                        .deliveryFailureCause(event.getDeliveryFailureCause())
                        .discoveredAddress(paperTrackings.getNotificationState().getDiscoveredAddress())
                        .statusDateTime(LocalDateTime.from(event.getStatusTimestamp().atZone(ZoneId.systemDefault())))
                        .clientRequestTimeStamp(LocalDateTime.from(event.getRequestTimestamp().atZone(ZoneId.systemDefault())))
                        .statusCode(ExternalChannelOutputsPayload.StatusCode.valueOf(StatusCodeConfiguration
                                .StatusCodeConfigurationEnum.fromKey(event.getStatusCode())
                                .getStatusCode().getValue()))
                        .statusDescription(StatusCodeConfiguration
                                .StatusCodeConfigurationEnum.fromKey(event.getStatusCode())
                                .getStatusCodeDescription())
                        .build())
                .build();
    }

    public static com.sngular.apigenerator.asyncapi.business_model.model.event.Attachment buildAttachmentForExternalChannelOutputEvent(Attachment attachment) {
        return com.sngular.apigenerator.asyncapi.business_model.model.event.Attachment.builder()
                .date(attachment.getDate().toString())
                .id(attachment.getId())
                .documentType(attachment.getDocumentType())
                .url(attachment.getUrl())
                .build();
    }
}
