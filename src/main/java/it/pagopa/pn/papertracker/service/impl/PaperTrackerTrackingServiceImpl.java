package it.pagopa.pn.papertracker.service.impl;

import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingCreationRequest;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingsRequest;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingsResponse;
import it.pagopa.pn.papertracker.mapper.PaperTrackingsMapper;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.service.PaperTrackerTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static it.pagopa.pn.papertracker.mapper.PaperTrackingsMapper.toPaperTrackings;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaperTrackerTrackingServiceImpl implements PaperTrackerTrackingService {

    private final PaperTrackingsDAO paperTrackingsDAO;

    @Override
    public Mono<Void> insertPaperTrackings(TrackingCreationRequest trackingCreationRequest) {
        log.info("Insert paper trackings by trackingCreationRequest: {}", trackingCreationRequest);
        return paperTrackingsDAO.putIfAbsent(toPaperTrackings(trackingCreationRequest)).then();
    }

    @Override
    public Mono<TrackingsResponse> retrieveTrackings(TrackingsRequest trackingsRequest) {
        log.info("Retrieve trackings by trackingsRequest: {}", trackingsRequest);
        return paperTrackingsDAO.retrieveAllByTrackingIds(trackingsRequest.getTrackingIds())
                .map(PaperTrackingsMapper::toTracking)
                .collectList()
                .doOnNext(trackings -> log.info("Retrieved {} trackings for request {}", trackings.size(), trackingsRequest))
                .map(trackings -> {
                    TrackingsResponse response = new TrackingsResponse();
                    response.setTrackings(trackings);
                    return response;
                });
    }

    @Override
    public Mono<Void> updatePaperTrackingsStatus(String trackingId, PaperTrackings paperTrackings) {
        return paperTrackingsDAO.updateItem(trackingId, paperTrackings)
                .then();
    }

    @Override
    public Mono<TrackingsResponse> retrieveTrackingsByAttemptId(String attemptId, String pcRetry) {
        log.info("Retrieve trackings by attemptId: {} with pcRetry: {}", attemptId, pcRetry);
        return paperTrackingsDAO.retrieveEntityByAttemptId(attemptId, pcRetry)
                .map(PaperTrackingsMapper::toTracking)
                .collectList()
                .doOnNext(trackings -> log.info("Retrieved {} trackings for attemptId {}", trackings.size(), attemptId))
                .map(trackings -> {
                    TrackingsResponse response = new TrackingsResponse();
                    response.setTrackings(trackings);
                    return response;
                });
    }

}
