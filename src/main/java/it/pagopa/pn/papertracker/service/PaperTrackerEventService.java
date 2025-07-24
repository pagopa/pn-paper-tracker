package it.pagopa.pn.papertracker.service;

import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsErrors;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackerCreationRequest;
import reactor.core.publisher.Mono;

public interface PaperTrackerEventService {

    Mono<Void> insertPaperTrackings(TrackerCreationRequest trackerCreationRequest);
    Mono<Void> insertPaperTrackingsErrors(PaperTrackingsErrors paperTrackingsErrors);

}
