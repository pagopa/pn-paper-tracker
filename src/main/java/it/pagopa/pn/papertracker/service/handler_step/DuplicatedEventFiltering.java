package it.pagopa.pn.papertracker.service.handler_step;

import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.AttachmentDetails;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.mapper.PaperTrackingsErrorsMapper;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.model.HandlerContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DuplicatedEventFiltering implements HandlerStep {

    /**
     * Step di validazione per filtrare eventi duplicati.
     * Se lo stato del tracciamento è "DONE" o "AWAITING_OCR", verifica se l'evento in arrivo è già presente nella lista degli eventi.
     * In caso di evento duplicato, genera un errore di validazione. Altrimenti, permette il proseguimento del flusso.
     * @param context Contesto contenente le informazioni necessarie per l'elaborazione dell'evento.
     * @return Mono(Void)
     */
    @Override
    public Mono<Void> execute(HandlerContext context) {
        PaperTrackings paperTrackings = context.getPaperTrackings();
        if(paperTrackings.getState().equals(PaperTrackingsState.DONE) || paperTrackings.getState().equals(PaperTrackingsState.AWAITING_OCR)) {
            PaperProgressStatusEvent paperProgressStatusEvent = context.getPaperProgressStatusEvent();
            paperTrackings.getEvents().stream()
                    .filter(event -> isDuplicatedEvent(event, paperProgressStatusEvent))
                    .findFirst()
                    .ifPresent(event -> Mono.error(new PnPaperTrackerValidationException("Duplicated event found: " + event.getStatusCode(),
                            PaperTrackingsErrorsMapper.buildPaperTrackingsError(paperTrackings,
                                    List.of(event.getStatusCode()),
                                    ErrorCategory.DUPLICATED_EVENT,
                                    null,
                                    "Duplicated event found for statusCode: " + event.getStatusCode(),
                                    FlowThrow.DUPLICATED_EVENT_VALIDATION,
                                    ErrorType.WARNING
                            ))));
        }
        return Mono.empty();
    }

    private boolean isDuplicatedEvent(Event event, PaperProgressStatusEvent paperProgressStatusEvent) {
        return event.getStatusCode().equalsIgnoreCase(paperProgressStatusEvent.getStatusCode())
                && event.getProductType().name().equals(paperProgressStatusEvent.getProductType())
                && event.getStatusTimestamp().toString().equals(paperProgressStatusEvent.getStatusDateTime().toString())
                && event.getDeliveryFailureCause().equalsIgnoreCase(paperProgressStatusEvent.getDeliveryFailureCause())
                && event.getRegisteredLetterCode().equalsIgnoreCase(paperProgressStatusEvent.getRegisteredLetterCode())
                && event.getStatusDescription().equalsIgnoreCase(paperProgressStatusEvent.getStatusDescription())
                && validateAttachments(event.getAttachments(), paperProgressStatusEvent.getAttachments());
    }

    private boolean validateAttachments(List<Attachment> attachments, List<AttachmentDetails> attachmentsDetails) {
       List<AttachmentDetails> remappedAttachments = attachments.stream().map(attachment -> {
            AttachmentDetails attachmentDetails = new AttachmentDetails();
            attachmentDetails.setId(attachment.getId());
            attachmentDetails.setDate(attachment.getDate().atOffset(ZoneOffset.UTC));
            attachmentDetails.setUri(attachment.getUri());
            attachmentDetails.setDocumentType(attachment.getDocumentType());
            return attachmentDetails;
        }).toList();
        return new HashSet<>(remappedAttachments).equals(new HashSet<>(attachmentsDetails));
    }


}
