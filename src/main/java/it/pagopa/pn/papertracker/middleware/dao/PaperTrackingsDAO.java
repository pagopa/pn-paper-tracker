package it.pagopa.pn.papertracker.middleware.dao;

import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface PaperTrackingsDAO {

    Mono<PaperTrackings> putIfAbsent(PaperTrackings entity);

    Mono<PaperTrackings> retrieveEntityByRequestIdAndCreatedAt(String requestId, Instant createdAt);

    Mono<PaperTrackings> updateItem(String requestId, Instant createdAt, PaperTrackings paperTrackings);

    Flux<PaperTrackings> retrieveEntityByOcrRequestId(String ocrRequestId);
}
