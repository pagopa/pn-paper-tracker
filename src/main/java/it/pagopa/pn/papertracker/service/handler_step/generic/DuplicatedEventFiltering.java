package it.pagopa.pn.papertracker.service.handler_step.generic;

import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.AttachmentDetails;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.mapper.PaperTrackingsErrorsMapper;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class DuplicatedEventFiltering implements HandlerStep {

    /**
     * Step di validazione per filtrare eventi duplicati.
     * Se lo stato del tracciamento è "DONE" o "AWAITING_OCR", verifica se l'evento in arrivo è già presente nella lista degli eventi.
     * In caso di evento duplicato, genera un errore di validazione. Altrimenti, permette il proseguimento del flusso.
     *
     * @param context Contesto contenente le informazioni necessarie per l'elaborazione dell'evento.
     * @return Mono(Void)
     */
    @Override
    public Mono<Void> execute(HandlerContext context) {
        PaperTrackings paperTrackings = context.getPaperTrackings();
        PaperProgressStatusEvent paperProgressStatusEvent = context.getPaperProgressStatusEvent();
        boolean isPresentDuplicate = false;
        if (paperTrackings.getState().equals(PaperTrackingsState.DONE) || paperTrackings.getState().equals(PaperTrackingsState.AWAITING_OCR)) {
            isPresentDuplicate = paperTrackings.getEvents().stream()
                    .anyMatch(event -> isDuplicatedEvent(event, paperProgressStatusEvent));
        }
        if (isPresentDuplicate) {
            return Mono.error(new PnPaperTrackerValidationException("Duplicated event found: " + paperProgressStatusEvent.getStatusCode(),
                    PaperTrackingsErrorsMapper.buildPaperTrackingsError(paperTrackings,
                            List.of(paperProgressStatusEvent.getStatusCode()),
                            ErrorCategory.DUPLICATED_EVENT,
                            null,
                            "Duplicated event found for statusCode: " + paperProgressStatusEvent.getStatusCode(),
                            FlowThrow.DUPLICATED_EVENT_VALIDATION,
                            ErrorType.WARNING
                    )));
        } else {
            return Mono.empty();
        }
    }

    private boolean isDuplicatedEvent(Event event, PaperProgressStatusEvent paperProgressStatusEvent) {
        return checkField(event.getStatusCode(), paperProgressStatusEvent.getStatusCode())
                && checkField(event.getProductType().name(), paperProgressStatusEvent.getProductType())
                && checkField(event.getStatusTimestamp().toString(), paperProgressStatusEvent.getStatusDateTime().toString())
                && checkField(event.getRegisteredLetterCode(), paperProgressStatusEvent.getRegisteredLetterCode())
                && checkField(event.getStatusDescription(), paperProgressStatusEvent.getStatusDescription())
                && checkField(event.getDeliveryFailureCause(), paperProgressStatusEvent.getDeliveryFailureCause())
                && validateAttachments(event.getAttachments(), paperProgressStatusEvent.getAttachments());
    }

    private boolean checkField(String oldEventField, String newEventField) {
        return (StringUtils.hasText(oldEventField) && StringUtils.hasText(newEventField) && oldEventField.equalsIgnoreCase(newEventField))
                || (!StringUtils.hasText(oldEventField) && !StringUtils.hasText(newEventField));
    }

    private boolean validateAttachments(List<Attachment> attachments, List<AttachmentDetails> attachmentsDetails) {
        List<AttachmentDetails> remappedAttachments = attachments.stream().map(attachment -> {
            AttachmentDetails attachmentDetails = new AttachmentDetails();
            attachmentDetails.setId(attachment.getId());
            if (Objects.nonNull(attachment.getDate())) {
                attachmentDetails.setDate(attachment.getDate().atOffset(ZoneOffset.UTC));
            }
            attachmentDetails.setUri(attachment.getUri());
            attachmentDetails.setDocumentType(attachment.getDocumentType());
            return attachmentDetails;
        }).toList();
        return new HashSet<>(remappedAttachments).equals(new HashSet<>(attachmentsDetails));
    }


}
