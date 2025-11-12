package it.pagopa.pn.papertracker.service.handler_step._890;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.config.TrackerConfigUtils;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.StatusCodeEnum;
import it.pagopa.pn.papertracker.mapper.SendEventMapper;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Attachment;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Event;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.model.DocumentTypeEnum;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static it.pagopa.pn.papertracker.model.EventStatusCodeEnum.RECAG012;

@Component
@RequiredArgsConstructor
@Slf4j
public class RECAG012EventChecker implements HandlerStep {

    private final PaperTrackingsDAO paperTrackingsDAO;
    private final TrackerConfigUtils trackerConfigUtils;

    /**
     * Step che effettua i seguenti passaggi:<br>
     * 1. Verifica se tutti gli allegati richiesti sono presenti tra gli eventi salvati.<br>
     *    - In caso contrario, il flusso prosegue con gli step successivi.<br>
     * 2. Verifica la presenza dell'evento RECAG012.<br>
     *    - Se non viene trovato, il flusso prosegue con gli step successivi.<br>
     *    - Se l'evento viene trovato, lo invia a delivery-push ed effettua un update in tabella
     *      impostando refined a true.
     *
     * @param context Contesto contenente le informazioni necessarie per l'elaborazione dell'evento.
     * @return Mono(Void)
     */
    @Override
    public Mono<Void> execute(HandlerContext context) {
        log.info("Starting RECAG012EventChecker for trackingId {}", context.getTrackingId());

        if (!hasAllRequiredAttachments(context)) {
            log.info("Missing required attachments for trackingId {}", context.getTrackingId());
            return Mono.empty();
        }
        return Mono.justOrEmpty(findRECAG012Event(context))
                .switchIfEmpty(logEventNotFound(context).then(Mono.empty()))
                .flatMap(event -> processEvent(context, event));
    }

    private boolean hasAllRequiredAttachments(HandlerContext context) {
        Set<String> documentTypes = getAttachmentTypes(context);
        return documentTypes.containsAll(trackerConfigUtils.getActualRequiredAttachmentsRefinementStock890(LocalDate.now()));
    }

    private Set<String> getAttachmentTypes(HandlerContext context) {
        return context.getPaperTrackings().getEvents().stream()
                .filter(event -> Objects.nonNull(event.getAttachments()) && !event.getAttachments().isEmpty())
                .flatMap(event -> event.getAttachments().stream())
                .map(Attachment::getDocumentType)
                .collect(Collectors.toSet());
    }

    private Optional<Event> findRECAG012Event(HandlerContext context) {
        return context.getPaperTrackings().getEvents().stream()
                .filter(event -> RECAG012.name().equalsIgnoreCase(event.getStatusCode()))
                .findFirst();
    }

    private Mono<Void> processEvent(HandlerContext context, Event event) {
        return SendEventMapper.createSendEventsFromEventEntity(
                        context.getTrackingId(), event, StatusCodeEnum.OK, event.getStatusCode(), OffsetDateTime.now())
                .doOnNext(context.getEventsToSend()::add)
                .doOnNext(sendEvent -> paperTrackingsDAO.updateItem(context.getTrackingId(), getPaperTrackingsToUpdate(context, event)))
                .then();
    }

    private Mono<Void> logEventNotFound(HandlerContext context) {
        return Mono.fromRunnable(() -> log.info("Event RECAG012 not found in trackingId {}", context.getTrackingId()));
    }

    private PaperTrackings getPaperTrackingsToUpdate(HandlerContext context, Event event) {
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setRefined(true);
        if(context.getEventId().equals(event.getId())) //imposto il timestamp solo quando l'evento RECAG012 arriva per la prima volta
            paperTrackings.setRecag012StatusTimestamp(Instant.now());
        return paperTrackings;
    }
}
