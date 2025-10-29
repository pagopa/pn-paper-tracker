package it.pagopa.pn.papertracker.service.impl;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingCreationRequest;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingsRequest;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingsResponse;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsState;
import it.pagopa.pn.papertracker.service.PaperTrackerTrackingService;
import it.pagopa.pn.papertracker.mapper.PaperTrackingsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

import static it.pagopa.pn.papertracker.mapper.PaperTrackingsMapper.toPaperTrackings;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaperTrackerTrackingServiceImpl implements PaperTrackerTrackingService {

    private final PaperTrackingsDAO paperTrackingsDAO;
    private final PnPaperTrackerConfigs pnPaperTrackerConfigs;

    @Override
    public Mono<Void> insertPaperTrackings(TrackingCreationRequest trackingCreationRequest) {
        Duration paperTrackingsTtlDuration = pnPaperTrackerConfigs.getPaperTrackingsTtlDuration();
        return paperTrackingsDAO.putIfAbsent(toPaperTrackings(trackingCreationRequest, paperTrackingsTtlDuration)).then();
    }

    @Override
    public Mono<TrackingsResponse> retrieveTrackings(TrackingsRequest trackingsRequest) {
        TrackingsResponse response = new TrackingsResponse();
        return paperTrackingsDAO.retrieveAllByTrackingIds(trackingsRequest.getTrackingIds())
                .map(PaperTrackingsMapper::toTracking)
                .collectList()
                .doOnNext(response::setTrackings)
                .thenReturn(response);
    }

    @Override
    public Mono<Void> updatePaperTrackingsStatus(String trackingId, PaperTrackings paperTrackings) {
        return paperTrackingsDAO.updateItem(trackingId, paperTrackings)
                .then();
    }

    @Override
    public Mono<TrackingsResponse> retrieveTrackingsByAttemptId(String attemptId, String pcRetry) {
        TrackingsResponse response = new TrackingsResponse();
        return paperTrackingsDAO.retrieveEntityByAttemptId(attemptId, pcRetry)
                .map(PaperTrackingsMapper::toTracking)
                .collectList()
                .doOnNext(response::setTrackings)
                .thenReturn(response);
    }

    @Override
    public Mono<Void> updatePaperTrackingsStatusForRework(String trackingId, String reworkId) {
        return paperTrackingsDAO.retrieveEntityByTrackingId(trackingId)
            .flatMap(paperTrackings -> {
                paperTrackings.setState(PaperTrackingsState.valueOf("AWAITING_REWORK_EVENTS"));
                paperTrackings.setReworkRequestTimestamp(Instant.now());
                paperTrackings.setReworkId(reworkId);
                return paperTrackingsDAO.updateItem(trackingId, paperTrackings).then();
            });
    }

}
