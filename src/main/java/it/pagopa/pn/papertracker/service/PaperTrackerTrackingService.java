package it.pagopa.pn.papertracker.service;

import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingCreationRequest;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingsRequest;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingsResponse;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsErrors;
import reactor.core.publisher.Mono;

public interface PaperTrackerTrackingService {

    Mono<Void> insertPaperTrackings(TrackingCreationRequest trackingCreationRequest);

    Mono<Void> insertPaperTrackingsErrors(PaperTrackingsErrors paperTrackingsErrors);

    Mono<TrackingsResponse> retrieveTrackings(TrackingsRequest trackingsRequest);

}
