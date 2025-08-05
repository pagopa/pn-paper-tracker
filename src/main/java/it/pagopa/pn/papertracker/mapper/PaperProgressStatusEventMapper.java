package it.pagopa.pn.papertracker.mapper;

import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.AttachmentDetails;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Attachment;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Event;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.model.HandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Mono;

import java.util.List;

public class PaperProgressStatusEventMapper {

    /**
     * Crea l'evento dalla classe PaperProgressStatusEvent e lo inserisce dentro PaperTrackins in modo da fare l'upsert
     *
     * @param handlerContext
     * @return PaperTrackings contenente il nuovo evento
     */
    public static Mono<PaperTrackings> createPaperTrackingFromPaperProgressStatusEvent(HandlerContext handlerContext) {
        PaperProgressStatusEvent paperProgressStatusEvent = handlerContext.getPaperProgressStatusEvent();
        PaperTrackings paperTrackings = new PaperTrackings();
        Event event = new Event();
        if (!CollectionUtils.isEmpty(paperProgressStatusEvent.getAttachments())) {
            event.setAttachments(paperProgressStatusEvent.getAttachments().stream()
                    .map(PaperProgressStatusEventMapper::buildAttachmentFromAttachmentDetail)
                    .toList());
        }
        event.setStatusCode(paperProgressStatusEvent.getStatusCode());
        event.setStatusTimestamp(paperProgressStatusEvent.getStatusDateTime().toInstant());
        event.setRequestTimestamp(paperProgressStatusEvent.getClientRequestTimeStamp().toInstant());
        event.setDeliveryFailureCause(paperProgressStatusEvent.getDeliveryFailureCause());
        event.setRegisteredLetterCode(paperProgressStatusEvent.getRegisteredLetterCode());
        event.setProductType(ProductType.valueOf(paperProgressStatusEvent.getProductType()));
        if (!StringUtils.isEmpty(handlerContext.getAnonimizedDiscoveredAddress())) {
            event.setDiscoveredAddress(handlerContext.getAnonimizedDiscoveredAddress());
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
