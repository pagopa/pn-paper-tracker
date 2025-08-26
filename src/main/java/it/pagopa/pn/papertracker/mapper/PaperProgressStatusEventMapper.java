package it.pagopa.pn.papertracker.mapper;

import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.AttachmentDetails;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

public class PaperProgressStatusEventMapper {

    /**
     * Rimappa l'oggetto PaperProgressStatusEvent nell'entity PaperTrackings in modo da effettuare l'upsert
     *
     * @param paperProgressStatusEvent evento da rimappare
     * @param anonymizedDiscoveredAddressId l'id dell'indirizzo anonimizzato
     * @return PaperTrackings contenente il nuovo evento
     */
    public static Mono<PaperTrackings> toPaperTrackings(PaperProgressStatusEvent paperProgressStatusEvent, String anonymizedDiscoveredAddressId, String eventId) {
        PaperTrackings paperTrackings = new PaperTrackings();
        Event event = new Event();
        event.setId(eventId);
        if (!CollectionUtils.isEmpty(paperProgressStatusEvent.getAttachments())) {
            event.setAttachments(paperProgressStatusEvent.getAttachments().stream()
                    .map(PaperProgressStatusEventMapper::buildAttachmentFromAttachmentDetail)
                    .toList());
            PaperStatus paperStatus = new PaperStatus();
            paperStatus.setFinalDematTimestamp(Instant.now());
            paperTrackings.setPaperStatus(paperStatus);
        }
        event.setStatusCode(paperProgressStatusEvent.getStatusCode());
        event.setStatusTimestamp(paperProgressStatusEvent.getStatusDateTime().toInstant());
        event.setRequestTimestamp(paperProgressStatusEvent.getClientRequestTimeStamp().toInstant());
        event.setDeliveryFailureCause(paperProgressStatusEvent.getDeliveryFailureCause());
        event.setRegisteredLetterCode(paperProgressStatusEvent.getRegisteredLetterCode());
        event.setProductType(ProductType.valueOf(paperProgressStatusEvent.getProductType()));
        event.setAnonymizedDiscoveredAddressId(anonymizedDiscoveredAddressId);

        paperTrackings.setEvents(List.of(event));
        return Mono.just(paperTrackings);
    }

    public static Attachment buildAttachmentFromAttachmentDetail(AttachmentDetails attachmentDetails) {
        Attachment attachment = new Attachment();
        attachment.setDate(attachmentDetails.getDate().toInstant());
        attachment.setId(attachmentDetails.getId());
        attachment.setDocumentType(attachmentDetails.getDocumentType());
        attachment.setUri(attachmentDetails.getUri());
        return attachment;
    }
}
