package it.pagopa.pn.papertracker.service.handler_step.AR;

import io.netty.util.internal.StringUtil;
import it.pagopa.pn.papertracker.config.SequenceConfiguration;
import it.pagopa.pn.papertracker.config.StatusCodeConfiguration;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.mapper.PaperTrackingsErrorsMapper;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.model.DeliveryFailureCauseEnum;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.model.SequenceElement;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SequenceValidator implements HandlerStep {

    private final PaperTrackingsDAO paperTrackingsDAO;
    private final SequenceConfiguration sequenceConfiguration;

    @Override
    public Mono<Void> execute(HandlerContext context) {
        return Mono.just(context.getPaperTrackings())
                .flatMap(this::validateSequence)
                .flatMap(updatedPaperTracking -> {
                    context.setPaperTrackings(updatedPaperTracking);
                    return Mono.empty();
                });
    }

    /**
     * Valida la sequenza degli eventi di una raccomandata, applicando una serie di controlli di business.
     *
     * @param paperTrackings oggetto contenente gli eventi da validare
     * @return Mono<Void> che completa se la validazione ha successo, altrimenti emette un Mono.error
     */
    public Mono<PaperTrackings> validateSequence(PaperTrackings paperTrackings) {
        log.info("Beginning validation for sequence for paper tracking : {}", paperTrackings);
        if (Objects.isNull(paperTrackings.getPaperStatus())) {
            paperTrackings.setPaperStatus(new PaperStatus());
        }
        if (Objects.isNull(paperTrackings.getValidationFlow())) {
            paperTrackings.setValidationFlow(new ValidationFlow());
        }
        return extractSequenceFromEvents(paperTrackings.getEvents(), paperTrackings)
                .flatMap(this::getOnlyLatestEvents)
                .flatMap(events -> validatePresenceOfStatusCodes(events ,paperTrackings))
                .flatMap(events -> validateBusinessTimestamps(events, paperTrackings))
                .flatMap(events -> validateAttachments(events, paperTrackings))
                .flatMap(events -> validateRegisteredLetterCode(events, paperTrackings))
                .flatMap(events -> validateDeliveryFailureCause(events, paperTrackings))
                .flatMap(events -> paperTrackingsDAO.updateItem(paperTrackings.getTrackingId(), enrichWithSequenceValidationTimestamp(paperTrackings)));
    }

    /**
     * Valida la presenza dei documenti necessari presenti nella lista di eventi in input. La lista dei documenti necessari
     * viene ottenuta dalle configurazione e nel caso in cui si abbiano documenti in più non richiesti non mandiamo in errore
     * ma continuiamo il flusso.
     * <p>
     * Esempio lista OK:
     * RECRN002D
     * RECRN002E[AR, INDAGINE]
     * RECRN002E[AR]
     * RECRN002F
     * Esempio lista KO:
     * RECRN002D
     * RECRN002E[PLICO]
     * RECRN002E[INDAGINE]
     * RECRN002F
     *
     * @param events        lista di eventi da cui estrarre la sequenza
     * @param paperTrackings  entità di paper tracking
     * @return Mono contenente la lista di eventi dati in input, altrimenti se la validazione non è andata a buona fine Mono.error()
     */
    private Mono<List<Event>> validateAttachments(List<Event> events, PaperTrackings paperTrackings) {
        log.info("Beginning validation for attachents for events : {}", events);

        Set<SequenceElement> sequenceElements = sequenceConfiguration.sequenceMap().get(events.getFirst().getStatusCode());
        HashMap<SequenceElement, List<String>> listOfDocumentsForSequenceElement = new HashMap<>();

        //Costruzione della mappa di documenti presenti negli eventi per ogni statusCode specifico
        buildMapOfDocumentsForSequenceElement(events, sequenceElements, listOfDocumentsForSequenceElement);

        //Per ogni elemento della sequenze di configurazione verifica la presenza dei documenti richiesti
        return Flux.fromIterable(listOfDocumentsForSequenceElement.entrySet())
                .flatMap(entrySet -> {
                    if (Objects.nonNull(entrySet.getKey().getDocumentTypes())) {
                        boolean allDocsMatch = entrySet.getKey().getDocumentTypes()
                                .stream()
                                .allMatch(docType -> entrySet.getValue().contains(docType.getValue()));
                        if (!allDocsMatch) {
                            return generateCustomError("Attachments are not valid for the sequence: " + sequenceElements,
                                    events, paperTrackings, ErrorCategory.ATTACHMENTS_ERROR
                            );
                        }
                    }

                    return Mono.empty();
                })
                .then(Mono.just(events));
    }

    private static void buildMapOfDocumentsForSequenceElement(List<Event> events, Set<SequenceElement> sequenceElements,
                                                              HashMap<SequenceElement, List<String>> listOfDocumentsForSequenceElement) {
        sequenceElements
                .forEach(seqElem -> {
                    List<String> documentsInEvents = events.stream()
                                    //Filtro la lista di eventi prendendo solamente quelli che hanno lo status code
                                    //uguale a quello dell'elemento di sequence
                                    .filter(event -> event.getStatusCode().equals(seqElem.getCode()))
                                    //Prendo la lista dei documenti presenti per tutti gli eventi
                                    .flatMap(event -> {
                                        if (!CollectionUtils.isEmpty(event.getAttachments())) {
                                            return event.getAttachments()
                                                    .stream()
                                                    .map(Attachment::getDocumentType);
                                        } else {
                                            return null;
                                        }}).toList();
                    //Se la lista di documenti presenti non è vuota allora la inserisco nella mappa per la validazione
                    if (!CollectionUtils.isEmpty(documentsInEvents)) {
                        listOfDocumentsForSequenceElement.put(seqElem, documentsInEvents);
                    }
                });
    }

    /**
     * Filtra la lista di eventi per ottenere solo gli ultimi eventi rilevanti.
     * Nel caso di eventi contententi documenti, il codice prenderà gli eventi che hanno un documento non presente nella
     * mappa dei documenti già presi
     *<p>
     * Ad esempio per la seguente lista prenderemo i seguenti eventi
     * -> RECRN002D
     * RECRN002E[INDAGINE]
     * -> RECRN002E[PLICO]
     * -> RECRN002E[INDAGINE]
     * RECRN002E[AR]
     * -> RECRN002E[AR]
     * -> RECRN002F
     * <p>
     * similmente nel caso di evento con più documenti, prende l'evento se uno tra i documenti dell'evento
     * non è nella mappa ad esempio:
     * -> RECRN002D
     * RECRN002E[INDAGINE]
     * -> RECRN002E[PLICO]
     * RECRN002E[INDAGINE]
     * -> RECRN002E[AR, INDAGINE]
     * -> RECRN002E[AR]
     * -> RECRN002F
     *
     * @param events lista di eventi da filtrare
     * @return Mono di eventi contenenti gli elementi filtrati dal metodo
     */
    public Mono<List<Event>> getOnlyLatestEvents(List<Event> events) {
        log.info("Beginning extraction of latest events from : {}", events);
        HashMap<String, Set<String>> uniqueCodes = new HashMap<>();
        List<Event> mutableEvents = new ArrayList<>(events);

        //Ordino la lista in base al timestamp e poi la inverto per avere al primo posto l'evento con requestTimestamp più alto
        mutableEvents.sort(Comparator.comparing(Event::getRequestTimestamp).reversed());

        return Mono.just(mutableEvents.stream().filter(
                event -> {
                    Set<String> documents = uniqueCodes.get(event.getStatusCode());
                    boolean allDocumentsInEventAreAlreadyPresentInMap =
                            documents != null &&
                                    event.getAttachments()
                                        .stream()
                                        .allMatch(document -> documents.contains(document.getDocumentType()));
                    if (allDocumentsInEventAreAlreadyPresentInMap) {
                        return false;
                    } else {
                        if (documents == null) {
                            uniqueCodes.put(event.getStatusCode(), new HashSet<>());
                        }
                        if (!CollectionUtils.isEmpty(event.getAttachments())) {
                            List<Attachment> documentsToAdd = event.getAttachments().stream()
                                    .filter(document -> !uniqueCodes.get(event.getStatusCode())
                                            .contains(document.getDocumentType()))
                                    .toList();
                            if (!CollectionUtils.isEmpty(documentsToAdd)) {
                                Set<String> eventDocuments = uniqueCodes.get(event.getStatusCode());
                                eventDocuments.addAll(documentsToAdd.stream().map(Attachment::getDocumentType).toList());
                                uniqueCodes.put(event.getStatusCode(), eventDocuments);
                            }
                        }

                        return true; // Evento unico, è l'ultimo
                    }
                }
        ).toList());
    }

    /**
     * Valida la presenza e la correttezza del delivery failure cause negli eventi.
     *
     * @param events lista di eventi da validare
     * @param paperTrackings oggetto principale della richiesta
     * @return Mono contenente la lista di eventi dati in input, altrimenti se la validazione non è andata a buona fine Mono.error()
     */
    private Mono<List<Event>> validateDeliveryFailureCause(List<Event> events, PaperTrackings paperTrackings) {
        log.info("Beginning validation for delivery failure cause for events : {}", events);

        for (Event event : events) {
            String statusCode = event.getStatusCode();
            String deliveryFailureCause = event.getDeliveryFailureCause();

            if (StatusCodeConfiguration.StatusCodeConfigurationEnum.RECRN002B.name().equals(statusCode) ||
                    StatusCodeConfiguration.StatusCodeConfigurationEnum.RECRN002E.name().equals(statusCode)) {
                if (StringUtil.isNullOrEmpty(deliveryFailureCause)) {
                    return generateCustomError(
                            "deliveryFailureCause is null for statusCode=" + statusCode,
                            events, paperTrackings, ErrorCategory.DELIVERY_FAILURE_CAUSE_ERROR
                    );
                }
                if ((StatusCodeConfiguration.StatusCodeConfigurationEnum.RECRN002B.name().equals(statusCode)
                        && !DeliveryFailureCauseEnum.containsCauseForB(deliveryFailureCause)) ||
                        (StatusCodeConfiguration.StatusCodeConfigurationEnum.RECRN002E.name().equals(statusCode)
                                && !DeliveryFailureCauseEnum.containsCauseForE(deliveryFailureCause))) {
                    return generateCustomError(
                            "Invalid deliveryFailureCause: " + deliveryFailureCause,
                            events, paperTrackings, ErrorCategory.DELIVERY_FAILURE_CAUSE_ERROR
                    );
                }

                paperTrackings.getPaperStatus().setDeliveryFailureCause(event.getDeliveryFailureCause());
            }
        }
        return Mono.just(events);
    }

    /**
     * Verifica che tutti i registered letter code degli eventi siano uguali.
     *
     * @param paperTrackings oggetto principale della richiesta
     * @param events lista di eventi da validare
     * @return Mono contenente la lista di eventi dati in input, altrimenti se la validazione non è andata a buona fine Mono.error()
     */
    private Mono<List<Event>> validateRegisteredLetterCode(List<Event> events, PaperTrackings paperTrackings) {
        log.info("Beginning validation for registered letter codes for events : {}", events);

        String firstRegisteredLetterCode = events.getFirst().getRegisteredLetterCode();
        // Tutti i registered letter code devono essere uguali nella sequenza
        boolean allRegisteredLetterCodeMatch = events.stream().allMatch(event -> event.getRegisteredLetterCode().equals(firstRegisteredLetterCode));
        if (!allRegisteredLetterCodeMatch) {
            return generateCustomError(
                    "Registered letter codes do not match in sequence: " + events.stream().map(Event::getRegisteredLetterCode).toList(),
                    events, paperTrackings, ErrorCategory.REGISTERED_LETTER_CODE_ERROR
            );
        }
        paperTrackings.getPaperStatus().setRegisteredLetterCode(firstRegisteredLetterCode);
        return Mono.just(events);
    }

    /**
     * Estrae la sequenza di eventi rilevanti (tripletta ABC/ DEF) dalla lista fornita.
     *
     * @param events        lista di eventi da cui estrarre la sequenza
     * @param paperTrackings entità di Paper tracking
     * @return Mono contenente la lista filtrata di eventi
     */
    private Mono<List<Event>> extractSequenceFromEvents(List<Event> events, PaperTrackings paperTrackings) {
        log.info("Beginning extraction of relevant events from : {}", events);
        Event lastEvent = events.getLast();
        Set<SequenceElement> sequenceElements = sequenceConfiguration.sequenceMap().get(lastEvent.getStatusCode());
        if (sequenceElements == null) {
            return generateCustomError(
                    "Invalid lastEvent for sequence validation",
                    events, paperTrackings, ErrorCategory.LAST_EVENT_EXTRACTION_ERROR
            );
        }
        return Mono.just(
                events.stream()
                        .filter(event -> sequenceElements.stream()
                                .anyMatch(element -> element.getCode().equals(event.getStatusCode())))
                        .toList());
    }

    /**
     * Valida la presenza di tutti gli eventi necessari per la sequenza data dall'evento finale.
     *
     * @param events lista di eventi da validare
     * @param paperTrackings entità di paper tracking
     * @return Mono contenente la lista di eventi dati in input, altrimenti se la validazione non è andata a buona fine Mono.error()
     */
    private Mono<List<Event>> validatePresenceOfStatusCodes(List<Event> events, PaperTrackings paperTrackings) {
        log.info("Beginning validation for required status codes for events : {}", events);

        Event firstEvent = events.getFirst();
        List<String> eventsStatusCodes = events.stream()
                .map(Event::getStatusCode)
                .toList();
        Set<SequenceElement> sequenceElements = sequenceConfiguration.sequenceMap().get(firstEvent.getStatusCode());
        return Flux.fromIterable(sequenceElements)
                .flatMap(seqElem -> Mono.just(seqElem.getCode()))
                .flatMap(statusCode -> {
                    if (!eventsStatusCodes.contains(statusCode)) {
                        return generateCustomError("Necessary status code not found in events", events, paperTrackings, ErrorCategory.STATUS_CODE_ERROR);
                    }
                    return Mono.just(statusCode);
                })
                .then(Mono.just(events));
    }

    /**
     * Verifica che i timestamp di business degli eventi siano coerenti tra loro. Questa validazione raggruppa gli eventi
     * in base ad un groupId specificato nella configurazione della sequence. Eventi con groupId uguali verranno comparati
     * tra di loro, eventi con groupId null non verranno validati.
     *
     * @param events lista di eventi da validare
     * @param paperTrackings entità di tracking
     * @return Mono contenente la lista di eventi dati in input, altrimenti se la validazione non è andata a buona fine Mono.error()
     */
    private Mono<List<Event>> validateBusinessTimestamps(List<Event> events, PaperTrackings paperTrackings) {
        log.info("Beginning validation for business timestamps for events : {}", events);

        Set<SequenceElement> sequenceElements = sequenceConfiguration.sequenceMap().get(events.getFirst().getStatusCode());
        List<String> groupIds = sequenceElements.stream()
                .map(SequenceElement::getDateValidationGroup)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return Flux.fromIterable(groupIds)
                .distinct()
                .flatMap(groupId -> Mono.just(events
                        .stream()
                        .filter(event -> {
                            SequenceElement seqElem = sequenceElements.stream()
                                    .filter(seqElement -> seqElement.getCode().equals(event.getStatusCode()))
                                    .findFirst()
                                    .orElse(null);
                            return seqElem != null && Objects.equals(seqElem.getDateValidationGroup(), groupId);
                        })
                        .toList()))
                .flatMap(groupEvents -> {
                    boolean allStatusTimestampInGroupAreEquals = groupEvents.stream()
                            .allMatch(groupEvent -> groupEvent.getStatusTimestamp().equals(groupEvents.getFirst().getStatusTimestamp()));
                    if(!allStatusTimestampInGroupAreEquals) {
                        return generateCustomError("Invalid business timestamps", events, paperTrackings, ErrorCategory.DATE_ERROR);
                    }
                    return Mono.just(groupEvents);
                }).then(Mono.just(events));
    }

    private Mono<List<Event>> generateCustomError(String message, List<Event> events, PaperTrackings paperTrackings, ErrorCategory errorCategory) {
        return Mono.error(new PnPaperTrackerValidationException(message, PaperTrackingsErrorsMapper.buildPaperTrackingsError(
                paperTrackings,
                events.stream().map(Event::getStatusCode).toList(),
                errorCategory,
                null,
                message,
                FlowThrow.SEQUENCE_VALIDATION,
                ErrorType.ERROR)));
    }

    private PaperTrackings enrichWithSequenceValidationTimestamp(PaperTrackings paperTrackings) {
        paperTrackings.getValidationFlow().setSequencesValidationTimestamp(Instant.now());
        return paperTrackings;
    }
    
}
