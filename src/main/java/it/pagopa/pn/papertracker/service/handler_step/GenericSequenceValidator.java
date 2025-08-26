package it.pagopa.pn.papertracker.service.handler_step;

import it.pagopa.pn.papertracker.config.SequenceConfiguration;
import it.pagopa.pn.papertracker.config.StatusCodeConfiguration;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.mapper.PaperTrackingsErrorsMapper;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.model.DeliveryFailureCauseEnum;
import it.pagopa.pn.papertracker.model.DocumentTypeEnum;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.model.SequenceElement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public abstract class GenericSequenceValidator implements HandlerStep{

    private final SequenceConfiguration sequenceConfiguration;
    private final PaperTrackingsDAO paperTrackingsDAO;


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
        PaperTrackings paperTrackingsToUpdate = new PaperTrackings();
        paperTrackingsToUpdate.setPaperStatus(new PaperStatus());
        log.info("Starting validation for sequence for paper tracking : {}", paperTrackings);
        return extractSequenceFromEvents(paperTrackings.getEvents(), paperTrackings)
                .filter(events -> !CollectionUtils.isEmpty(events))
                .switchIfEmpty(generateCustomError("Invalid lastEvent for sequence validation", paperTrackings.getEvents(), paperTrackings, ErrorCategory.LAST_EVENT_EXTRACTION_ERROR))
                .flatMap(this::getOnlyLatestEvents)
                .flatMap(events -> validatePresenceOfStatusCodes(events, paperTrackings))
                .flatMap(events -> validateBusinessTimestamps(events, paperTrackings))
                .flatMap(events -> validateAttachments(events, paperTrackings))
                .flatMap(events -> validateRegisteredLetterCode(events, paperTrackings, paperTrackingsToUpdate))
                .flatMap(events -> validateDeliveryFailureCause(events, paperTrackings, paperTrackingsToUpdate))
                .flatMap(events -> paperTrackingsDAO.updateItem(paperTrackings.getTrackingId(), enrichWithSequenceValidationTimestamp(events, paperTrackingsToUpdate)));
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
     * @param events         lista di eventi da cui estrarre la sequenza
     * @param paperTrackings entità di paper tracking
     * @return Mono contenente la lista di eventi dati in input, altrimenti se la validazione non è andata a buona fine Mono.error()
     */
    private Mono<List<Event>> validateAttachments(List<Event> events, PaperTrackings paperTrackings) {
        log.info("Beginning validation for attachents for events : {}", events);

        Set<SequenceElement> sequenceElements = Optional.ofNullable(sequenceConfiguration.sequenceMap().get(events.getFirst().getStatusCode())).orElseGet(Set::of);
        Map<String, List<String>> statusCodeReceivedAttachments = events.stream()
                .collect(Collectors.groupingBy(
                        Event::getStatusCode,
                        Collectors.flatMapping(
                                e -> Optional.ofNullable(e.getAttachments()).orElse(List.of()).stream().map(Attachment::getDocumentType),
                                Collectors.toList()
                        )
                ));

        return Flux.fromIterable(sequenceElements)
                .filter(sequenceElement -> !CollectionUtils.isEmpty(sequenceElement.getDocumentTypes()))
                .flatMap(sequenceElement -> {
                    List<String> documentTypes = statusCodeReceivedAttachments.getOrDefault(sequenceElement.getCode(), List.of());
                    return checkAttachments(documentTypes, sequenceElement, events, paperTrackings);
                })
                .then()
                .thenReturn(events);
    }

    private Mono<Void> checkAttachments(List<String> documentTypes, SequenceElement sequenceElement, List<Event> events, PaperTrackings paperTrackings) {
        if (new HashSet<>(documentTypes).containsAll(sequenceElement.getDocumentTypes().stream().map(DocumentTypeEnum::getValue).toList())) {
            return Mono.empty();
        } else {
            return Mono.error(new PnPaperTrackerValidationException("Attachments are not valid for the sequence element: " + sequenceElement, PaperTrackingsErrorsMapper.buildPaperTrackingsError(
                    paperTrackings,
                    events.stream().map(Event::getStatusCode).toList(),
                    ErrorCategory.ATTACHMENTS_ERROR,
                    null,
                    "Attachments are not valid for the sequence element: " + sequenceElement,
                    FlowThrow.SEQUENCE_VALIDATION,
                    ErrorType.ERROR)));
        }
    }

    /**
     * Filtra la lista di eventi per ottenere solo gli ultimi eventi rilevanti.
     * Nel caso di eventi contententi documenti, il codice prenderà gli eventi che hanno un documento non presente nella
     * mappa dei documenti già presi
     * <p>
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

        //Ordino la lista in base al timestamp e poi la inverto per avere al primo posto l'evento con requestTimestamp più recente
        mutableEvents.sort(Comparator.comparing(Event::getRequestTimestamp).reversed());

        return Mono.just(mutableEvents.stream().filter(
                event -> {
                    Set<String> documents = uniqueCodes.get(event.getStatusCode());
                    boolean allDocumentsInEventAreAlreadyPresentInMap =
                            documents != null &&
                                    Optional.ofNullable(event.getAttachments()).orElse(new ArrayList<>())
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
                        return true;
                    }
                }
        ).toList());
    }

    /**
     * Valida la presenza e la correttezza del delivery failure cause negli eventi.
     *
     * @param events         lista di eventi da validare
     * @param paperTrackings oggetto principale della richiesta
     * @return Mono contenente la lista di eventi dati in input, altrimenti se la validazione non è andata a buona fine Mono.error()
     */
    private Mono<List<Event>> validateDeliveryFailureCause(List<Event> events, PaperTrackings paperTrackings, PaperTrackings paperTrackingsToUpdate) {
        log.info("Beginning validation for delivery failure cause for events : {}", events);
        return Flux.fromIterable(events)
                .flatMap(event -> {
                    String deliveryFailureCause = event.getDeliveryFailureCause();
                    StatusCodeConfiguration.StatusCodeConfigurationEnum statusCodeEnum = StatusCodeConfiguration.StatusCodeConfigurationEnum.fromKey(event.getStatusCode());
                    if (CollectionUtils.isEmpty(statusCodeEnum.getDeliveryFailureCauseList()) || statusCodeEnum.getDeliveryFailureCauseList().contains(DeliveryFailureCauseEnum.fromValue(deliveryFailureCause))) {
                        if (StringUtils.hasText(deliveryFailureCause)) {
                            paperTrackingsToUpdate.getPaperStatus().setDeliveryFailureCause(event.getDeliveryFailureCause());
                        }
                        return Mono.empty();
                    } else {
                        return generateCustomError("Invalid deliveryFailureCause: " + deliveryFailureCause, events, paperTrackings, ErrorCategory.DELIVERY_FAILURE_CAUSE_ERROR);
                    }
                })
                .then()
                .thenReturn(events);
    }

    /**
     * Verifica che tutti i registered letter code degli eventi siano uguali.
     *
     * @param paperTrackings oggetto principale della richiesta
     * @param events         lista di eventi da validare
     * @return Mono contenente la lista di eventi dati in input, altrimenti se la validazione non è andata a buona fine Mono.error()
     */
    private Mono<List<Event>> validateRegisteredLetterCode(List<Event> events, PaperTrackings paperTrackings, PaperTrackings paperTrackingsToUpdate) {
        log.info("Beginning validation for registered letter codes for events : {}", events);

        String firstRegisteredLetterCode = events.getFirst().getRegisteredLetterCode();
        boolean allRegisteredLetterCodeMatch = events.stream().allMatch(event -> event.getRegisteredLetterCode().equals(firstRegisteredLetterCode));
        return Mono.just(allRegisteredLetterCodeMatch)
                .flatMap(registeredLetterCodeMatch -> {
                    if (!registeredLetterCodeMatch) {
                        return generateCustomError("Registered letter codes do not match in sequence: " + events.stream().map(Event::getRegisteredLetterCode).toList(), events, paperTrackings, ErrorCategory.REGISTERED_LETTER_CODE_ERROR);
                    }
                    paperTrackingsToUpdate.getPaperStatus().setRegisteredLetterCode(firstRegisteredLetterCode);
                    return Mono.empty();
                })
                .thenReturn(events);
    }

    /**
     * Estrae la sequenza di eventi rilevanti (tripletta ABC/ DEF) dalla lista fornita.
     *
     * @param events         lista di eventi da cui estrarre la sequenza
     * @param paperTrackings entità di Paper tracking
     * @return Mono contenente la lista filtrata di eventi
     */
    private Mono<List<Event>> extractSequenceFromEvents(List<Event> events, PaperTrackings paperTrackings) {
        log.info("Beginning extraction of relevant events from : {}", events);
        Set<SequenceElement> sequenceElements = Optional.ofNullable(sequenceConfiguration.sequenceMap().get(events.getLast().getStatusCode())).orElseGet(Set::of);
        return Mono.just(events.stream().filter(event -> sequenceElements.stream().anyMatch(element -> element.getCode().equals(event.getStatusCode()))).toList());
    }

    /**
     * Valida la presenza di tutti gli eventi necessari per la sequenza data dall'evento finale.
     *
     * @param events         lista di eventi da validare
     * @param paperTrackings entità di paper tracking
     * @return Mono contenente la lista di eventi dati in input, altrimenti se la validazione non è andata a buona fine Mono.error()
     */
    private Mono<List<Event>> validatePresenceOfStatusCodes(List<Event> events, PaperTrackings paperTrackings) {
        log.info("Beginning validation for required status codes for events : {}", events);

        List<String> eventsStatusCodes = events.stream().map(Event::getStatusCode).toList();
        Set<SequenceElement> sequenceElements = Optional.ofNullable(sequenceConfiguration.sequenceMap().get(events.getFirst().getStatusCode())).orElseGet(Set::of);
        return Flux.fromIterable(sequenceElements)
                .map(SequenceElement::getCode)
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
     * @param events         lista di eventi da validare
     * @param paperTrackings entità di tracking
     * @return Mono contenente la lista di eventi dati in input, altrimenti se la validazione non è andata a buona fine Mono.error()
     */
    private Mono<List<Event>> validateBusinessTimestamps(List<Event> events, PaperTrackings paperTrackings) {
        log.info("Beginning validation for business timestamps for events : {}", events);

        Set<SequenceElement> sequenceElements = Optional.ofNullable(sequenceConfiguration.sequenceMap().get(events.getFirst().getStatusCode())).orElseGet(Set::of);
        List<String> groupIds = sequenceElements.stream()
                .map(SequenceElement::getDateValidationGroup)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        return Flux.fromIterable(groupIds)
                .flatMap(groupId -> Mono.just(events.stream()
                        .filter(event -> {
                            SequenceElement seqElem = sequenceElements.stream()
                                    .filter(seqElement -> seqElement.getCode().equals(event.getStatusCode()))
                                    .findFirst()
                                    .orElse(null);
                            return seqElem != null && Objects.equals(seqElem.getDateValidationGroup(), groupId);
                        })
                        .toList()))
                .flatMap(groupEvents -> {
                    boolean allStatusTimestampInGroupAreEquals = groupEvents.stream().allMatch(groupEvent -> groupEvent.getStatusTimestamp().equals(groupEvents.getFirst().getStatusTimestamp()));
                    if (!allStatusTimestampInGroupAreEquals) {
                        return generateCustomError("Invalid business timestamps", events, paperTrackings, ErrorCategory.DATE_ERROR);
                    }
                    return Mono.just(groupEvents);
                }).then(Mono.just(events));
    }

    private <T> Mono<T> generateCustomError(String message, List<Event> events, PaperTrackings paperTrackings, ErrorCategory errorCategory) {
        return Mono.error(new PnPaperTrackerValidationException(message, PaperTrackingsErrorsMapper.buildPaperTrackingsError(
                paperTrackings,
                events.stream().map(Event::getStatusCode).toList(),
                errorCategory,
                null,
                message,
                FlowThrow.SEQUENCE_VALIDATION,
                ErrorType.ERROR)));
    }

    private PaperTrackings enrichWithSequenceValidationTimestamp(List<Event> events, PaperTrackings paperTrackingsToUpdate) {
        Instant now = Instant.now();
        ValidationFlow validationFlow = new ValidationFlow();
        validationFlow.setSequencesValidationTimestamp(now);
        paperTrackingsToUpdate.setValidationFlow(validationFlow);
        paperTrackingsToUpdate.getPaperStatus().setValidatedEvents(events);
        paperTrackingsToUpdate.getPaperStatus().setValidatedSequenceTimestamp(now);
        return paperTrackingsToUpdate;
    }

}
