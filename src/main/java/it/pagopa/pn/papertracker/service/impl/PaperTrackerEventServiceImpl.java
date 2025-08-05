package it.pagopa.pn.papertracker.service.impl;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingCreationRequest;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsErrorsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsErrors;
import it.pagopa.pn.papertracker.service.PaperTrackerEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static it.pagopa.pn.papertracker.service.mapper.PaperTrackingsMapper.toPaperTrackings;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaperTrackerEventServiceImpl implements PaperTrackerEventService {

    private final PaperTrackingsDAO paperTrackingsDAO;
    private final PaperTrackingsErrorsDAO paperTrackingsErrorsDAO;
    private final PnPaperTrackerConfigs pnPaperTrackerConfigs;

    @Override
    public Mono<Void> insertPaperTrackings(TrackingCreationRequest trackingCreationRequest) {
        Duration paperTrackingsTtlDuration = pnPaperTrackerConfigs.getPaperTrackingsTtlDuration();
        return paperTrackingsDAO.putIfAbsent(toPaperTrackings(trackingCreationRequest, paperTrackingsTtlDuration)).then();
    }

    @Override
    public Mono<Void> insertPaperTrackingsErrors(PaperTrackingsErrors paperTrackingsErrors) {
        log.info("Inserting paper trackings error: {}", paperTrackingsErrors.toString());
        return paperTrackingsErrorsDAO.insertError(paperTrackingsErrors).then();
    }

}
