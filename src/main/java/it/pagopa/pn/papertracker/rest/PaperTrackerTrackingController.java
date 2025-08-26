package it.pagopa.pn.papertracker.rest;

import it.pagopa.pn.papertracker.generated.openapi.server.v1.api.PaperTrackerTrackingApi;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingCreationRequest;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingsRequest;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingsResponse;
import it.pagopa.pn.papertracker.service.PaperTrackerTrackingService;
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
public class PaperTrackerTrackingController implements PaperTrackerTrackingApi {

    private final PaperTrackerTrackingService paperTrackerEventService;

    /**
     * Api chiamata da pn-paper-channel per inizializzare l'oggetto PaperTrackings.
     * <p>
     * Riceve una richiesta con i dati da salvare, la elabora tramite il servizio
     * {@code paperTrackerEventService} e restituisce una risposta HTTP 201 (CREATED) se l'operazione ha successo.
     *
     * @param trackingCreationRequest richiesta con i dati da salvare
     * @return status HTTP
     */
    @Override
    public Mono<ResponseEntity<Void>> initTracking(Mono<TrackingCreationRequest> trackingCreationRequest, final ServerWebExchange exchange) {
        log.info("Received request initTracking - trackingCreationRequest={}", trackingCreationRequest);

        return trackingCreationRequest
                .flatMap(paperTrackerEventService::insertPaperTrackings)
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED).build());
    }

    @Override
    public Mono<ResponseEntity<TrackingsResponse>> retrieveTrackings(Mono<TrackingsRequest> trackingsRequest, ServerWebExchange exchange) {
        log.info("Received request retrieveTrackings - trackingsRequest={}", trackingsRequest);

        return trackingsRequest.flatMap(paperTrackerEventService::retrieveTrackings)
                .map(ResponseEntity::ok);
    }
}