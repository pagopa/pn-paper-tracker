package it.pagopa.pn.papertracker.service.handler_step.generic;

import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.mapper.PaperTrackingsErrorsMapper;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.model.DeliveryFailureCauseEnum;
import it.pagopa.pn.papertracker.model.EventStatusCodeEnum;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.model.sequence.SequenceConfig;
import it.pagopa.pn.papertracker.model.sequence.SequenceConfiguration;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import it.pagopa.pn.papertracker.utils.TrackerUtility;
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

import static it.pagopa.pn.papertracker.model.DocumentTypeEnum.ARCAD;
import static it.pagopa.pn.papertracker.model.DocumentTypeEnum.CAD;
import static it.pagopa.pn.papertracker.model.PredictedRefinementType.PRE10;

@Service
@RequiredArgsConstructor
@Slf4j
public abstract class GenericSequenceValidator implements HandlerStep {

    private final PaperTrackingsDAO paperTrackingsDAO;


    /**
     * Step di validazione della sequenza degli eventi di una raccomandata.
     * La validazione include controlli sulla presenza di eventi necessari, coerenza dei timestamp
     * di business, validità dei documenti allegati, coerenza dei codici di lettera raccomandata e
     * correttezza della causa di mancata consegna.
     *
     * @param context Contesto contenente le informazioni necessarie per l'elaborazione dell'evento.
     * @return Mono(Void)
     */
    @Override
    public Mono<Void> execute(HandlerContext context) {
        SequenceConfig sequenceConfig = SequenceConfiguration.getConfig(context.getPaperProgressStatusEvent().getStatusCode());

        log.debug("Executing GenericSequenceValidator with sequence config: {}", sequenceConfig);
        return Mono.just(context.getPaperTrackings())
                .flatMap(paperTrackings -> validateSequence(paperTrackings, context, sequenceConfig, true))
                .doOnNext(TrackerUtility::checkValidationConfig)//aggiunto per retrocompatibilità
                .doOnNext(context::setPaperTrackings)
                .then();
    }

    /**
     * Valida la sequenza degli eventi di una raccomandata, applicando una serie di controlli di business.
     *
     * @param paperTrackings oggetto contenente gli eventi da validare
     * @return Mono<Void> che completa se la validazione ha successo, altrimenti emette un Mono.error
     */
    public Mono<PaperTrackings> validateSequence(PaperTrackings paperTrackings, HandlerContext context, SequenceConfig sequenceConfig, Boolean strictFinalEventValidation) {
        PaperTrackings paperTrackingsToUpdate = new PaperTrackings();
        paperTrackingsToUpdate.setPaperStatus(new PaperStatus());
        log.info("Starting validation for sequence for paper tracking : {}", paperTrackings);
        return extractSequenceFromEvents(paperTrackings.getEvents(), sequenceConfig.sequenceStatusCodes())
                .filter(events -> !CollectionUtils.isEmpty(events))
                .switchIfEmpty(generateCustomError("Invalid lastEvent for sequence validation", context, paperTrackings, ErrorCategory.LAST_EVENT_EXTRACTION_ERROR,strictFinalEventValidation))
                .flatMap(this::getOnlyLatestEvents)
                .flatMap(events -> validatePresenceOfStatusCodes(events, paperTrackings, context, sequenceConfig.requiredStatusCodes(),strictFinalEventValidation))
                .flatMap(events -> validateBusinessTimestamps(events, paperTrackings, context, sequenceConfig,strictFinalEventValidation, paperTrackingsToUpdate))
                .flatMap(events -> validateAttachments(events, paperTrackings, context, sequenceConfig.validAttachments(), sequenceConfig.requiredAttachments(),strictFinalEventValidation))
                .flatMap(events -> validateRegisteredLetterCode(events, paperTrackings, paperTrackingsToUpdate, context,strictFinalEventValidation))
                .flatMap(events -> validateDeliveryFailureCause(events, paperTrackings, context,strictFinalEventValidation))
                .flatMap(events -> enrichPaperTrackingToUpdateWithAddressAndFailureCause(events, paperTrackingsToUpdate, context.getPaperProgressStatusEvent().getStatusCode()))
                .flatMap(events -> paperTrackingsDAO.updateItem(paperTrackings.getTrackingId(), enrichWithSequenceValidationTimestamp(events, paperTrackingsToUpdate)));
    }

    private Mono<List<Event>> enrichPaperTrackingToUpdateWithAddressAndFailureCause(List<Event> events, PaperTrackings paperTrackingsToUpdate, String statusCode) {
        return Mono.justOrEmpty(events.stream()
                        .filter(e -> e.getStatusCode().equalsIgnoreCase(preCloseMetaStatusCode(statusCode)))
                        .findFirst())
                .doOnNext(preCloseEvent -> {
                    if (StringUtils.hasText(preCloseEvent.getAnonymizedDiscoveredAddressId())) {
                        paperTrackingsToUpdate.getPaperStatus().setAnonymizedDiscoveredAddress(preCloseEvent.getAnonymizedDiscoveredAddressId());
                    }
                    if (StringUtils.hasText(preCloseEvent.getDeliveryFailureCause())) {
                        paperTrackingsToUpdate.getPaperStatus().setDeliveryFailureCause(preCloseEvent.getDeliveryFailureCause());
                    }
                })
                .thenReturn(events);
    }

    private String preCloseMetaStatusCode(String finalStatusCode) {
        String lastLetter = finalStatusCode.substring(finalStatusCode.length() - 1);
        if (lastLetter.equals("C")) {
            return finalStatusCode.substring(0, finalStatusCode.length() - 1) + "A";
        } else if (lastLetter.equals("F")) {
            return finalStatusCode.substring(0, finalStatusCode.length() - 1) + "D";
        }
        return finalStatusCode;
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
    private Mono<List<Event>> validateAttachments(List<Event> events, PaperTrackings paperTrackings, HandlerContext context,
                                                  Map<String, Set<String>> validAttachments, Set<String> requiredAttachments, Boolean strictFinalEventValidation) {
        log.info("Beginning attachment validation for events: {}", events);

        var eventsWithAttachments = events.stream()
                .filter(event -> !CollectionUtils.isEmpty(event.getAttachments()))
                .toList();

        var allDocs = eventsWithAttachments.stream()
                .flatMap(event -> event.getAttachments().stream().map(Attachment::getDocumentType))
                .collect(Collectors.toSet());

        return verifyRequiredAttachments(events, paperTrackings, context, requiredAttachments, allDocs, strictFinalEventValidation)
                .flatMap(events1 -> verifyValidAttachments(events, paperTrackings, context, validAttachments, eventsWithAttachments, strictFinalEventValidation))
                .doOnNext(unused -> log.info("Attachments validation completed successfully"));
    }

    private Mono<List<Event>> verifyRequiredAttachments(List<Event> events, PaperTrackings paperTrackings, HandlerContext context, Set<String> requiredAttachments, Set<String> allDocs, Boolean strictFinalEventValidation) {
        var missingDocs = new HashSet<>(requiredAttachments);
        missingDocs.removeAll(allDocs);
        if (missingDocs.isEmpty() || isPresentArcadOrCadForStock890(missingDocs)) {
            return Mono.just(events);
        }
        return generateCustomError("Missed required attachments for the sequence validation: " + missingDocs, context, paperTrackings, ErrorCategory.ATTACHMENTS_ERROR, strictFinalEventValidation);
    }

    private boolean isPresentArcadOrCadForStock890(HashSet<String> missingDocs) {
        return missingDocs.size() == 1 && (missingDocs.contains(ARCAD.getValue()) || missingDocs.contains(CAD.getValue()));
    }

    private Mono<List<Event>> verifyValidAttachments(List<Event> events, PaperTrackings paperTrackings, HandlerContext context, Map<String, Set<String>> validAttachments, List<Event> eventsWithAttachments, Boolean strictFinalEventValidation) {
        for (Event e : eventsWithAttachments) {
            var allowedDocs = validAttachments.get(e.getStatusCode());

            var eventDocs = e.getAttachments().stream()
                    .map(Attachment::getDocumentType)
                    .collect(Collectors.toSet());

            // Se l'evento contiene documenti non ammessi
            var invalidDocs = eventDocs.stream()
                    .filter(doc -> CollectionUtils.isEmpty(allowedDocs) || !allowedDocs.contains(doc))
                    .collect(Collectors.toSet());

            if (!CollectionUtils.isEmpty(invalidDocs)) {
                return generateCustomError("Event " + e.getStatusCode() + " contains invalid attachments: " + invalidDocs, context, paperTrackings, ErrorCategory.ATTACHMENTS_ERROR, strictFinalEventValidation);
            }
        }
        return Mono.just(events);
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
     * Valida la presenza e la correttezza della deliveryFailureCause negli eventi.<br>
     * Il controllo viene effettuato sulla base di quanto censito nell'enum {@link it.pagopa.pn.papertracker.model.EventStatusCodeEnum}:<br>
     * - Se per lo status code dell'evento, come deliveryFailureCauseList, risluta censita SKIP_VALIDATION,
     * allora la validazione viene saltata<br>
     * - Se per lo status code dell'evento, come deliveryFailureCauseList, risulta censita una lista vuota o una lista con valori specifici,
     * allora viene controllato che la deliveryFailureCause dell'evento sia assente (nel primo caso) o presente e valida (nel secondo caso)<br>
     *
     * @param events         lista di eventi da validare
     * @param paperTrackings oggetto principale della richiesta
     * @return Mono contenente la lista di eventi dati in input, altrimenti se la validazione non è andata a buona fine Mono.error()
     */
    private Mono<List<Event>> validateDeliveryFailureCause(List<Event> events, PaperTrackings paperTrackings, HandlerContext context, Boolean strictFinalEventValidation) {
        log.info("Beginning validation for delivery failure cause for events : {}", events);
        return Flux.fromIterable(events)
                .flatMap(event -> {
                    String deliveryFailureCause = event.getDeliveryFailureCause();
                    EventStatusCodeEnum statusCodeEnum = EventStatusCodeEnum.fromKey(event.getStatusCode());
                    List<DeliveryFailureCauseEnum> allowedCauses = statusCodeEnum.getDeliveryFailureCauseList();

                    if (allowedCauses.contains(DeliveryFailureCauseEnum.SKIP_VALIDATION) ||
                            (CollectionUtils.isEmpty(allowedCauses) && !StringUtils.hasText(deliveryFailureCause)) ||
                            (allowedCauses.contains(DeliveryFailureCauseEnum.fromValue(deliveryFailureCause)))) {
                        return Mono.just(event);
                    }

                    return generateCustomError(
                            "Invalid deliveryFailureCause: " + deliveryFailureCause,
                            context,
                            paperTrackings,
                            ErrorCategory.DELIVERY_FAILURE_CAUSE_ERROR,
                            strictFinalEventValidation
                    );
                })
                .collectList();
    }

    /**
     * Verifica che tutti i registered letter code degli eventi siano uguali.
     *
     * @param paperTrackings oggetto principale della richiesta
     * @param events         lista di eventi da validare
     * @return Mono contenente la lista di eventi dati in input, altrimenti se la validazione non è andata a buona fine Mono.error()
     */
    private Mono<List<Event>> validateRegisteredLetterCode(List<Event> events, PaperTrackings paperTrackings, PaperTrackings paperTrackingsToUpdate, HandlerContext context, Boolean strictFinalEventValidation) {
        log.info("Beginning validation for registered letter codes for events : {}", events);

        String firstRegisteredLetterCode = events.getFirst().getRegisteredLetterCode();
        boolean allRegisteredLetterCodeMatch = events.stream().allMatch(event -> event.getRegisteredLetterCode().equals(firstRegisteredLetterCode));
        return Mono.just(allRegisteredLetterCodeMatch)
                .flatMap(registeredLetterCodeMatch -> {
                    if (!registeredLetterCodeMatch) {
                        return generateCustomError("Registered letter codes do not match in sequence: " + events.stream().map(Event::getRegisteredLetterCode).toList(), context, paperTrackings, ErrorCategory.REGISTERED_LETTER_CODE_ERROR, strictFinalEventValidation);
                    }
                    paperTrackingsToUpdate.getPaperStatus().setRegisteredLetterCode(firstRegisteredLetterCode);
                    return Mono.empty();
                })
                .thenReturn(events);
    }

    /**
     * Estrae la sequenza di eventi rilevanti (tripletta ABC/ DEF) dalla lista fornita.
     *
     * @param events lista di eventi da cui estrarre la sequenza
     * @return Mono contenente la lista filtrata di eventi
     */
    private Mono<List<Event>> extractSequenceFromEvents(List<Event> events, Set<String> requiredStatusCodes) {
        log.info("Beginning extraction of relevant events from : {}", events);
        return Mono.just(events.stream()
                .filter(e -> requiredStatusCodes.contains(e.getStatusCode()))
                .toList());
    }

    /**
     * Valida la presenza di tutti gli eventi necessari per la sequenza data dall'evento finale.
     *
     * @param events         lista di eventi da validare
     * @param paperTrackings entità di paper tracking
     * @return Mono contenente la lista di eventi dati in input, altrimenti se la validazione non è andata a buona fine Mono.error()
     */
    private Mono<List<Event>> validatePresenceOfStatusCodes(List<Event> events, PaperTrackings paperTrackings, HandlerContext context, Set<String> requiredStatusCodes, Boolean strictFinalEventValidation) {
        log.info("Beginning validation for required status codes for events : {}", events);

        Set<String> eventStatusCodes = events.stream().map(Event::getStatusCode).collect(Collectors.toSet());
        Set<String> missingStatusCodes = new HashSet<>(requiredStatusCodes);
        missingStatusCodes.removeAll(eventStatusCodes);
        if (!missingStatusCodes.isEmpty()) {
            return generateCustomError(
                    "Necessary status code not found in events: " + missingStatusCodes,
                    context,
                    paperTrackings,
                    ErrorCategory.STATUS_CODE_ERROR,
                    strictFinalEventValidation
            );
        }
        return Mono.just(events);
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
    private Mono<List<Event>> validateBusinessTimestamps(List<Event> events, PaperTrackings paperTrackings, HandlerContext context, SequenceConfig sequenceConfig, Boolean strictFinalEventValidation, PaperTrackings paperTrackingsToUpdate) {
        log.info("Beginning validation for business timestamps for events : {}", events);
        Set<String> finalGroup = sequenceConfig.dateValidationGroupForFinalEvents();
        Set<String> stockGroup = sequenceConfig.dateValidationGroupForStockEvents();

        Instant validFinal = allStatusTimestampAreEquals(events, finalGroup);
        boolean validStock = allStockStatusTimestampAreEquals(events, stockGroup);

        if (Objects.nonNull(validFinal) && validStock && checkPredictedRefinementTypeIfStock890(paperTrackings, validFinal)){
            paperTrackingsToUpdate.getPaperStatus().setValidatedSequenceTimestamp(validFinal);
            return Mono.just(events);
        }
        return generateCustomError("Invalid business timestamps", context, paperTrackings, ErrorCategory.DATE_ERROR, strictFinalEventValidation);
    }

    private boolean checkPredictedRefinementTypeIfStock890(PaperTrackings paperTrackings, Instant validFinal) {
        if(StringUtils.hasText(paperTrackings.getPaperStatus().getPredictedRefinementType())
            && paperTrackings.getPaperStatus().getPredictedRefinementType().equalsIgnoreCase(PRE10.name())){
            Optional<Event> RECAG012Event = TrackerUtility.findRECAG012Event(paperTrackings);
            return RECAG012Event.map(event -> event.getStatusTimestamp().equals(validFinal)).orElse(true);
        }
        return true;
    }

    private Instant allStatusTimestampAreEquals(List<Event> events, Set<String> group) {
        List<Instant> timestamps = events.stream()
                .filter(e -> group.contains(e.getStatusCode()))
                .map(Event::getStatusTimestamp)
                .toList();
        if(timestamps.size() <= 1 || timestamps.stream().allMatch(t -> t.equals(timestamps.getFirst()))){
            return timestamps.getFirst();
        }
        return null;
    }

    private boolean allStockStatusTimestampAreEquals(List<Event> events, Set<String> group) {
        List<Instant> timestamps = events.stream()
                .filter(e -> group.contains(e.getStatusCode()))
                .map(Event::getStatusTimestamp)
                .toList();
        return timestamps.size() <= 1 || timestamps.stream().allMatch(t -> t.equals(timestamps.getFirst()));
    }

    private <T> Mono<T> generateCustomError(String message, HandlerContext context, PaperTrackings paperTrackings, ErrorCategory errorCategory, Boolean strictFinalEventValidation) {
        return Mono.error(new PnPaperTrackerValidationException(message, PaperTrackingsErrorsMapper.buildPaperTrackingsError(
                paperTrackings,
                context.getPaperProgressStatusEvent().getStatusCode(),
                errorCategory,
                null,
                message,
                FlowThrow.SEQUENCE_VALIDATION,
                Boolean.TRUE.equals(strictFinalEventValidation) ? ErrorType.ERROR : ErrorType.WARNING,
                context.getEventId())));
    }

    private PaperTrackings enrichWithSequenceValidationTimestamp(List<Event> events, PaperTrackings paperTrackingsToUpdate) {
        ValidationFlow validationFlow = new ValidationFlow();
        validationFlow.setSequencesValidationTimestamp(Instant.now());
        paperTrackingsToUpdate.setValidationFlow(validationFlow);
        paperTrackingsToUpdate.getPaperStatus().setValidatedEvents(events.stream().map(Event::getId).toList());
        return paperTrackingsToUpdate;
    }

}
