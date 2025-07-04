package it.pagopa.pn.papertracker.middleware.dao;

import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Event;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsEntity;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ValidationFlow;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PaperTrackingsDAO {

    Mono<PaperTrackingsEntity> putPaperTrackings(PaperTrackingsEntity entity);

    Mono<PaperTrackingsEntity> getPaperTrackings(String requestId);

    Mono<Void> updatePaperTrackingsEvent(String requestId, Event event);

    Mono<Void> updatePaperTrackingsValidationFlow(String requestId, ValidationFlow validationFlow);

    Flux<PaperTrackingsEntity> queryPaperTrackings(String ocrRequestId);
}
