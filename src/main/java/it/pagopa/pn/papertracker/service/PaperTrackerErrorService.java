package it.pagopa.pn.papertracker.service;

import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingErrorsResponse;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingsRequest;
import reactor.core.publisher.Mono;

public interface PaperTrackerErrorService {

    Mono<TrackingErrorsResponse> retrieveTrackingErrors(TrackingsRequest trackingsRequest);
}
