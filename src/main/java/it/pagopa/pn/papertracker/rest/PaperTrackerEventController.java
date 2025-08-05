package it.pagopa.pn.papertracker.rest;

import it.pagopa.pn.papertracker.generated.openapi.server.v1.api.PaperTrackerEventApi;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackerCreationRequest;
import it.pagopa.pn.papertracker.service.PaperTrackerEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@Slf4j
@RequiredArgsConstructor
public class PaperTrackerEventController implements PaperTrackerEventApi {

    private final PaperTrackerEventService paperTrackerEventService;

    /**
     * Api chiamata da pn-paper-channel per inizializzare l'oggetto PaperTrackings.
     * <p>
     * Riceve una richiesta con i dati da salvare, la elabora tramite il servizio
     * {@code paperTrackerEventService} e restituisce una risposta HTTP 201 (CREATED) se l'operazione ha successo.
     *
     * @param trackerCreationRequest richiesta con i dati da salvare
     * @return status HTTP
     */
    @Override
    public Mono<ResponseEntity<Void>> initTracking(Mono<TrackerCreationRequest> trackerCreationRequest, final ServerWebExchange exchange) {
        log.debug("Received request initTracking - trackerCreationRequest={}", trackerCreationRequest);

        return trackerCreationRequest
                .flatMap(paperTrackerEventService::insertPaperTrackings)
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED).build());
    }
}