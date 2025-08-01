package it.pagopa.pn.papertracker.service.handler_step;

import it.pagopa.pn.papertracker.mapper.PaperProgressStatusEventMapper;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.model.HandlerContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class MetadataUpserter implements HandlerStep {

    private final PaperTrackingsDAO paperTrackingsDAO;

    @Override
    public Mono<Void> execute(HandlerContext context) {
        return Mono.just(context)
                .flatMap(this::discoveredAddressAnonimization)
                .flatMap(PaperProgressStatusEventMapper::createPaperTrackingFromPaperProgressStatusEvent)
                .flatMap(paperTrackings -> paperTrackingsDAO.updateItem(context.getPaperProgressStatusEvent().getRequestId(), paperTrackings))
                .doOnNext(context::setPaperTrackings)
                .then();
    }

    private Mono<HandlerContext> discoveredAddressAnonimization(HandlerContext handlerContext) {
        //TODO implementare l'anonimizzazione del discoveredAddress
        if (Objects.nonNull(handlerContext.getPaperProgressStatusEvent().getDiscoveredAddress())) {
            String anonimizedDiscoveredAddress = "";
            handlerContext.setAnonimizedDiscoveredAddress(anonimizedDiscoveredAddress);
        }

        return Mono.just(handlerContext);
    }
}