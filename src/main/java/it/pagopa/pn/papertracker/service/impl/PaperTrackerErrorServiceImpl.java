package it.pagopa.pn.papertracker.service.impl;

import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingError;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingErrorsResponse;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingErrorsResponseResultsInner;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingsRequest;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsErrorsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsErrors;
import it.pagopa.pn.papertracker.service.PaperTrackerErrorService;
import it.pagopa.pn.papertracker.service.mapper.PaperTrackingsErrorsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaperTrackerErrorServiceImpl implements PaperTrackerErrorService {

    private final PaperTrackingsErrorsDAO paperTrackingsErrorsDAO;

    @Override
    public Mono<TrackingErrorsResponse> retrieveTrackingErrors(TrackingsRequest trackingsRequest) {
        return Flux.fromIterable(trackingsRequest.getTrackingIds())
                .flatMap(trackingId -> paperTrackingsErrorsDAO.retrieveErrors(trackingId)
                        .collectList()
                        .map(paperTrackingsErrors -> {
                            List<TrackingError> trackingErrorList = paperTrackingsErrors.stream()
                                    .map(PaperTrackingsErrorsMapper::toTrackingError)
                                    .toList();
                            TrackingErrorsResponseResultsInner trackingErrorsResponseResultsInner = new TrackingErrorsResponseResultsInner();
                            trackingErrorsResponseResultsInner.setTrackingId(trackingId);
                            trackingErrorsResponseResultsInner.setErrors(trackingErrorList);
                            return trackingErrorsResponseResultsInner;
                        }))
                .collectList()
                .map(trackingErrorsResponseResultsInnerList -> {
                    TrackingErrorsResponse trackingErrorsResponse = new TrackingErrorsResponse();
                    trackingErrorsResponse.setResults(trackingErrorsResponseResultsInnerList);
                    return trackingErrorsResponse;
                });
    }

    @Override
    public Mono<PaperTrackingsErrors> insertPaperTrackingsErrors(PaperTrackingsErrors paperTrackingsErrors) {
        log.info("Inserting paper trackings error: {}", paperTrackingsErrors.toString());
        return paperTrackingsErrorsDAO.insertError(paperTrackingsErrors).thenReturn(paperTrackingsErrors);
    }
}
