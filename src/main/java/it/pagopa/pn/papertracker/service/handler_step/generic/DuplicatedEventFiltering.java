package it.pagopa.pn.papertracker.service.handler_step.generic;

import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.AttachmentDetails;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.DiscoveredAddress;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.pndatavault.model.PaperAddress;
import it.pagopa.pn.papertracker.mapper.PaperTrackingsErrorsMapper;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.middleware.msclient.DataVaultClient;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@CustomLog
public class DuplicatedEventFiltering implements HandlerStep {

    private final DataVaultClient dataVaultClient;

    /**
     * Step di validazione per filtrare eventi duplicati.
     * Verifica se l'evento in arrivo è già presente nella lista degli eventi.
     * Escludo dal controllo duplicati eventi con lo stesso ID (es. ultimo evento ricevuto precedentemente salvato
     * e retry dello stesso messaggio).
     * In caso di evento duplicato, genera un errore di validazione. Altrimenti, permette il proseguimento del flusso.
     *
     * @param context Contesto contenente le informazioni necessarie per l'elaborazione dell'evento.
     * @return Mono(Void)
     */
    @Override
    public Mono<Void> execute(HandlerContext context) {
        log.info("Starting DuplicatedEventFiltering for trackingId: {}", context.getTrackingId());

        PaperTrackings paperTrackings = context.getPaperTrackings();
        PaperProgressStatusEvent paperProgressStatusEvent = context.getPaperProgressStatusEvent();

        return Flux.fromIterable(paperTrackings.getEvents())
                .filter(event -> !event.getId().equalsIgnoreCase(context.getEventId()))
                .flatMap(event -> isDuplicatedEvent(event, paperProgressStatusEvent))
                .any(Boolean::booleanValue)
                .flatMap(isDuplicate -> isDuplicate
                        ? Mono.error(createValidationException(paperTrackings, paperProgressStatusEvent, context))
                        : Mono.empty());
    }

    private PnPaperTrackerValidationException createValidationException(PaperTrackings paperTrackings,
                                                                        PaperProgressStatusEvent event,
                                                                        HandlerContext context) {
        String statusCode = event.getStatusCode();
        return new PnPaperTrackerValidationException(
                "Duplicated event found: " + statusCode,
                PaperTrackingsErrorsMapper.buildPaperTrackingsError(
                        paperTrackings,
                        statusCode,
                        ErrorCategory.DUPLICATED_EVENT,
                        null,
                        "Duplicated event found for statusCode: " + statusCode,
                        FlowThrow.DUPLICATED_EVENT_VALIDATION,
                        ErrorType.WARNING,
                        context.getEventId()
                )
        );
    }

    private Mono<Boolean> isDuplicatedEvent(Event event, PaperProgressStatusEvent paperProgressStatusEvent) {
        boolean fieldsMatch =
                checkField(event.getStatusCode(), paperProgressStatusEvent.getStatusCode()) &&
                        checkField(event.getProductType().name(), paperProgressStatusEvent.getProductType()) &&
                        checkField(event.getStatusTimestamp().toString(), paperProgressStatusEvent.getStatusDateTime().toString()) &&
                        checkField(event.getRegisteredLetterCode(), paperProgressStatusEvent.getRegisteredLetterCode()) &&
                        checkField(event.getStatusDescription(), paperProgressStatusEvent.getStatusDescription()) &&
                        checkField(event.getDeliveryFailureCause(), paperProgressStatusEvent.getDeliveryFailureCause()) &&
                        checkField(event.getIun(), paperProgressStatusEvent.getIun()) &&
                        validateAttachments(event.getAttachments(), paperProgressStatusEvent.getAttachments());

        if (!fieldsMatch) {
            return Mono.just(false);
        }

        return deanonymizeAddressAndCheckIt(event.getAnonymizedDiscoveredAddressId(), paperProgressStatusEvent);
    }

    private boolean checkField(String oldEventField, String newEventField) {
        return (StringUtils.hasText(oldEventField) && StringUtils.hasText(newEventField) && oldEventField.equalsIgnoreCase(newEventField))
                || (!StringUtils.hasText(oldEventField) && !StringUtils.hasText(newEventField));
    }

    private boolean validateAttachments(List<Attachment> attachments, List<AttachmentDetails> attachmentsDetails) {
        if (Objects.isNull(attachments) && Objects.isNull(attachmentsDetails))
            return true;
        if (Objects.isNull(attachments) || Objects.isNull(attachmentsDetails))
            return false;

        List<AttachmentDetails> remappedAttachments = attachments.stream().map(attachment -> {
            AttachmentDetails attachmentDetails = new AttachmentDetails();
            attachmentDetails.setId(attachment.getId());
            if (Objects.nonNull(attachment.getDate())) {
                attachmentDetails.setDate(attachment.getDate().atOffset(ZoneOffset.UTC));
            }
            attachmentDetails.setDocumentType(attachment.getDocumentType());
            attachmentDetails.setSha256(attachment.getSha256());
            return attachmentDetails;
        }).toList();

        List<AttachmentDetails> attachmentsDetailsNoUri = attachmentsDetails.stream().map(att -> {
            AttachmentDetails copy = new AttachmentDetails();
            copy.setId(att.getId());
            copy.setDate(att.getDate());
            copy.setDocumentType(att.getDocumentType());
            copy.setSha256(att.getSha256());
            return copy;
        }).toList();
        return new HashSet<>(remappedAttachments).equals(new HashSet<>(attachmentsDetailsNoUri));
    }

    private Mono<Boolean> deanonymizeAddressAndCheckIt(String anonymizedDiscoveredAddressId, PaperProgressStatusEvent paperProgressStatusEvent) {
        if (StringUtils.hasText(anonymizedDiscoveredAddressId) && Objects.nonNull(paperProgressStatusEvent.getDiscoveredAddress()))
            return dataVaultClient.deAnonymizeDiscoveredAddress(paperProgressStatusEvent.getRequestId(), anonymizedDiscoveredAddressId)
                    .map(paperAddress -> checkDiscoveredAddress(paperAddress, paperProgressStatusEvent.getDiscoveredAddress()));
        return Mono.just(!StringUtils.hasText(anonymizedDiscoveredAddressId) && Objects.isNull(paperProgressStatusEvent.getDiscoveredAddress()));
    }

    private boolean checkDiscoveredAddress(PaperAddress paperAddress, DiscoveredAddress discoveredAddress) {
        return checkField(paperAddress.getName(), discoveredAddress.getName())
                && checkField(paperAddress.getNameRow2(), discoveredAddress.getNameRow2())
                && checkField(paperAddress.getAddress(), discoveredAddress.getAddress())
                && checkField(paperAddress.getAddressRow2(), discoveredAddress.getAddressRow2())
                && checkField(paperAddress.getCap(), discoveredAddress.getCap())
                && checkField(paperAddress.getCity(), discoveredAddress.getCity())
                && checkField(paperAddress.getCity2(), discoveredAddress.getCity2())
                && checkField(paperAddress.getPr(), discoveredAddress.getPr())
                && checkField(paperAddress.getCountry(), discoveredAddress.getCountry());
    }


}
