package it.pagopa.pn.papertracker.service.impl;

import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingErrorsResponse;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingErrorsResponseResultsInner;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingsRequest;
import it.pagopa.pn.papertracker.mapper.PaperTrackerErrorEventMapper;
import it.pagopa.pn.papertracker.mapper.PaperTrackerMapStructMapper;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsErrorsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsErrors;
import it.pagopa.pn.papertracker.middleware.queue.producer.PaperTrackerErrorsMomProducer;
import it.pagopa.pn.papertracker.service.PaperTrackerErrorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaperTrackerErrorServiceImpl implements PaperTrackerErrorService {

    private final PaperTrackingsErrorsDAO paperTrackingsErrorsDAO;
    private final PaperTrackerMapStructMapper mapper;
    private final PaperTrackerErrorsMomProducer paperTrackerErrorsMomProducer;

    @Override
    public Mono<TrackingErrorsResponse> retrieveTrackingErrors(TrackingsRequest trackingsRequest) {
        TrackingErrorsResponse trackingErrorsResponse = new TrackingErrorsResponse();
        return Flux.fromIterable(trackingsRequest.getTrackingIds())
                .flatMap(trackingId -> paperTrackingsErrorsDAO.retrieveErrors(trackingId)
                        .map(mapper::toTrackingError)
                        .collectList()
                        .map(trackingErrors -> {
                                    TrackingErrorsResponseResultsInner trackingErrorsResponseResultsInner = new TrackingErrorsResponseResultsInner();
                                    trackingErrorsResponseResultsInner.setTrackingId(trackingId);
                                    trackingErrorsResponseResultsInner.setErrors(trackingErrors);
                                    return trackingErrorsResponseResultsInner;
                                }
                        ))
                .collectList()
                .doOnNext(trackingErrorsResponse::setResults)
                .map(trackingErrorsResponseResultsInners -> trackingErrorsResponse);
    }

    @Override
    public Mono<PaperTrackingsErrors> insertPaperTrackingsErrors(PaperTrackingsErrors paperTrackingsErrors) {
        log.info("Inserting paper trackings error: {}", paperTrackingsErrors.toString());
        return paperTrackingsErrorsDAO.insertError(paperTrackingsErrors)
                .then(sendToErrorsQueue(paperTrackingsErrors))
                .thenReturn(paperTrackingsErrors);
    }

    /**
     * Pubblica l'errore, già persistito su PaperTrackingsErrors, anche sulla coda degli errori.
     * <p>
     * L'invio è best-effort: un eventuale fallimento viene solo loggato e non propagato,
     * per evitare che il messaggio in ingresso venga riprocessato generando errori duplicati
     * sulla tabella PaperTrackingsErrors.
     */
    private Mono<Void> sendToErrorsQueue(PaperTrackingsErrors paperTrackingsErrors) {
        return Mono.fromRunnable(() -> paperTrackerErrorsMomProducer.push(PaperTrackerErrorEventMapper.toPaperTrackerErrorEvent(paperTrackingsErrors)))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(unused -> log.info("Sent paper trackings error to errors queue for trackingId: {}", paperTrackingsErrors.getTrackingId()))
                .onErrorResume(throwable -> {
                    log.error("Unable to send paper trackings error to errors queue for trackingId: {}", paperTrackingsErrors.getTrackingId(), throwable);
                    return Mono.empty();
                })
                .then();
    }
}
