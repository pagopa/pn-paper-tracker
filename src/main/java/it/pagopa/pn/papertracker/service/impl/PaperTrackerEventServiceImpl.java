package it.pagopa.pn.papertracker.service.impl;

import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsErrorsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsErrors;
import it.pagopa.pn.papertracker.service.PaperTrackerEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaperTrackerEventServiceImpl implements PaperTrackerEventService {

    private final PaperTrackingsErrorsDAO paperTrackingsErrorsDAO;

    @Override
    public Mono<Void> insertPaperTrackingsErrors(PaperTrackingsErrors paperTrackingsErrors) {
        log.info("Inserting paper trackings error: {}", paperTrackingsErrors.toString());
        return paperTrackingsErrorsDAO.insertError(paperTrackingsErrors).then();
    }

}
