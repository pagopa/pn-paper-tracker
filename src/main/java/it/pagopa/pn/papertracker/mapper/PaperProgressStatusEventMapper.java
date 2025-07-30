package it.pagopa.pn.papertracker.mapper;

import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.AttachmentDetails;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Attachment;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Event;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Mono;

import java.util.List;

public class PaperProgressStatusEventMapper {

    /**
     * Crea l'evento dalla classe PaperProgressStatusEvent e lo inserisce dentro PaperTrackins in modo da fare l'upsert
     *
     * @param paperProgressStatusEvent
     * @return PaperTrackings contenente il nuovo evento
     */
    public static Mono<PaperTrackings> createPaperTrackingFromPaperProgressStatusEvent(PaperProgressStatusEvent paperProgressStatusEvent) {
        PaperTrackings paperTrackings = new PaperTrackings();
        Event event = new Event();
        if (!CollectionUtils.isEmpty(paperProgressStatusEvent.getAttachments())) {
            event.setAttachments(paperProgressStatusEvent.getAttachments().stream()
                    .map(PaperProgressStatusEventMapper::buildAttachmentFromAttachmentDetail)
                    .toList());
        }
        event.setStatusCode(paperProgressStatusEvent.getStatusCode());
        event.setRequestTimestamp(paperProgressStatusEvent.getClientRequestTimeStamp().toInstant());
        event.setDeliveryFailureCause(paperProgressStatusEvent.getDeliveryFailureCause());
        event.setRegisteredLetterCode(paperProgressStatusEvent.getRegisteredLetterCode());
        event.setProductType(ProductType.valueOf(paperProgressStatusEvent.getProductType()));
        //TODO In che formato vogliamo il discoveredAddress?
        if (paperProgressStatusEvent.getDiscoveredAddress() != null) {
            event.setDiscoveredAddress(paperProgressStatusEvent.getDiscoveredAddress().toString());
        }

        paperTrackings.setEvents(List.of(event));
        return Mono.just(paperTrackings);
    }

    public static Attachment buildAttachmentFromAttachmentDetail(AttachmentDetails attachmentDetails) {
        Attachment attachment = new Attachment();
        attachment.setDate(attachmentDetails.getDate().toInstant());
        attachment.setId(attachmentDetails.getId());
        attachment.setDocumentType(attachmentDetails.getDocumentType());
        attachment.setUrl(attachmentDetails.getUri());
        return attachment;
    }
}
