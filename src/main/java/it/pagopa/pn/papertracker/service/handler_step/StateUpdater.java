package it.pagopa.pn.papertracker.service.handler_step;

import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.model.HandlerContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class StateUpdater implements HandlerStep {

    private final PaperTrackingsDAO paperTrackingsDAO;

    @Override
    public Mono<Void> execute(HandlerContext context) {
        return paperTrackingsDAO.updateItem(context.getPaperTrackings().getTrackingId(), context.getPaperTrackings())
                .then();
    }
}
