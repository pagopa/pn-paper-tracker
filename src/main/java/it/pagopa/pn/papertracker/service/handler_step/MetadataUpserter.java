package it.pagopa.pn.papertracker.service.handler_step;

import it.pagopa.pn.papertracker.mapper.PaperProgressStatusEventMapper;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.model.HandlerContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MetadataUpserter implements HandlerStep {

    private final PaperTrackingsDAO paperTrackingsDAO;

    @Override
    public Mono<Void> execute(HandlerContext context) {
        return Mono.just(context.getPaperProgressStatusEvent())
                .flatMap(PaperProgressStatusEventMapper::createPaperTrackingFromPaperProgressStatusEvent)
                //TODO createdAt va bene che sia getStatusDateTime?
                .flatMap(paperTrackings -> paperTrackingsDAO.updateItem(context.getPaperProgressStatusEvent().getRequestId(), context.getPaperProgressStatusEvent().getStatusDateTime().toInstant(), paperTrackings))
                .then();
    }
}