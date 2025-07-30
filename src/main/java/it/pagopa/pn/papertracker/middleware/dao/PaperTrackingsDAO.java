package it.pagopa.pn.papertracker.middleware.dao;

import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PaperTrackingsDAO {

    Mono<PaperTrackings> putIfAbsent(PaperTrackings entity);

    Mono<PaperTrackings> retrieveEntityByRequestId(String requestId);

    Mono<PaperTrackings> updateItem(String requestId, PaperTrackings paperTrackings);

    Flux<PaperTrackings> retrieveEntityByOcrRequestId(String ocrRequestId);
}
