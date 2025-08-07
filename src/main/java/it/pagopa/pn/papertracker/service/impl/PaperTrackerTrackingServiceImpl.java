package it.pagopa.pn.papertracker.service.impl;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingCreationRequest;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingsRequest;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingsResponse;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsState;
import it.pagopa.pn.papertracker.service.PaperTrackerTrackingService;
import it.pagopa.pn.papertracker.service.mapper.PaperTrackingsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static it.pagopa.pn.papertracker.service.mapper.PaperTrackingsMapper.toPaperTrackings;

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
        return paperTrackingsDAO.retrieveAllByTrackingIds(trackingsRequest.getTrackingIds())
                .collectList()
                .map(paperTrackings -> {
                    TrackingsResponse response = new TrackingsResponse();
                    response.setTrackings(
                            paperTrackings.stream()
                                    .map(PaperTrackingsMapper::toTracking)
                                    .collect(java.util.stream.Collectors.toList())
                    );
                    return response;
                });
    }

    @Override
    public Mono<Void> updatePaperTrackingsStatus(String trackingId, PaperTrackings paperTrackings) {
        return paperTrackingsDAO.updateItem(trackingId, paperTrackings)
                .then();
    }

}
