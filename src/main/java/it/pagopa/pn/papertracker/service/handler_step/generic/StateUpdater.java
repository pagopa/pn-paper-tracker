package it.pagopa.pn.papertracker.service.handler_step.generic;

import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class StateUpdater implements HandlerStep {

    private final PaperTrackingsDAO paperTrackingsDAO;

    /**
     * Step che aggiorna lo stato del tracking nel database.
     * @param context Contesto contenente le informazioni necessarie per l'elaborazione dell'evento.
     * @return Mono(Void)
     */
    @Override
    public Mono<Void> execute(HandlerContext context) {
        return paperTrackingsDAO.updateItem(context.getTrackingId(), context.getPaperTrackings())
                .then();
    }
}
