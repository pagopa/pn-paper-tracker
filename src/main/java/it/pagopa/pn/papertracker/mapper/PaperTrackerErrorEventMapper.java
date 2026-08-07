package it.pagopa.pn.papertracker.mapper;

import com.sngular.apigenerator.asyncapi.business_model.model.event.PaperTrackerErrorDetailsDTO;
import com.sngular.apigenerator.asyncapi.business_model.model.event.PaperTrackerErrorPayloadDTO;
import it.pagopa.pn.api.dto.events.GenericEventHeader;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsErrors;
import it.pagopa.pn.papertracker.middleware.queue.model.PaperTrackerErrorEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static it.pagopa.pn.papertracker.utils.QueueConst.PAPER_TRACKER_ERROR_EVENT_TYPE;
import static it.pagopa.pn.papertracker.utils.QueueConst.PUBLISHER;

@RequiredArgsConstructor(access = AccessLevel.NONE)
public class PaperTrackerErrorEventMapper {

    public static final String PAYLOAD_VERSION = "1.0.0";

    public static PaperTrackerErrorEvent toPaperTrackerErrorEvent(PaperTrackingsErrors paperTrackingsErrors) {
        GenericEventHeader header = GenericEventHeader.builder()
                .publisher(PUBLISHER)
                .eventId(UUID.randomUUID().toString())
                .createdAt(Instant.now())
                .eventType(PAPER_TRACKER_ERROR_EVENT_TYPE)
                .build();

        return new PaperTrackerErrorEvent(header, toPayload(paperTrackingsErrors));
    }

    private static PaperTrackerErrorPayloadDTO toPayload(PaperTrackingsErrors paperTrackingsErrors) {
        return PaperTrackerErrorPayloadDTO.builder()
                .version(PAYLOAD_VERSION)
                .trackingId(paperTrackingsErrors.getTrackingId())
                .created(Objects.requireNonNullElseGet(paperTrackingsErrors.getCreated(), Instant::now).toString())
                .category(Optional.ofNullable(paperTrackingsErrors.getErrorCategory()).map(Enum::name).orElse(null))
                .type(Optional.ofNullable(paperTrackingsErrors.getType())
                        .map(type -> PaperTrackerErrorPayloadDTO.Type.valueOf(type.name()))
                        .orElse(null))
                .flowThrow(Optional.ofNullable(paperTrackingsErrors.getFlowThrow()).map(Enum::name).orElse(null))
                .eventThrow(paperTrackingsErrors.getEventThrow())
                .eventIdThrow(paperTrackingsErrors.getEventIdThrow())
                .productType(paperTrackingsErrors.getProductType())
                .details(toDetails(paperTrackingsErrors))
                .build();
    }

    private static PaperTrackerErrorDetailsDTO toDetails(PaperTrackingsErrors paperTrackingsErrors) {
        return Optional.ofNullable(paperTrackingsErrors.getDetails())
                .map(details -> PaperTrackerErrorDetailsDTO.builder()
                        .cause(Optional.ofNullable(details.getCause()).map(Enum::name).orElse(null))
                        .message(details.getMessage())
                        .additionalDetails(details.getAdditionalDetails())
                        .build())
                .orElse(null);
    }

}
