package it.pagopa.pn.papertracker.service.handler_step;

import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.DiscoveredAddress;
import it.pagopa.pn.papertracker.mapper.PaperProgressStatusEventMapper;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.msclient.DataVaultClient;
import it.pagopa.pn.papertracker.model.HandlerContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class MetadataUpserter implements HandlerStep {

    private final PaperTrackingsDAO paperTrackingsDAO;
    private final DataVaultClient dataVaultClient;

    /**
     * Step utilizzato per l'upsert dei metadati relativi all'evento di avanzamento. Inoltre se è presente l'indirizzo scoperto, si occupa di anonimizzarlo.
     *  Alla fine del processo l'entità aggiornata viene settata nel context insieme al trackingId.
     * @param context Contesto contenente le informazioni necessarie per l'elaborazione dell'evento.
     * @return Mono(Void)
     */
    @Override
    public Mono<Void> execute(HandlerContext context) {
        return Mono.just(context)
                .flatMap(this::discoveredAddressAnonimization)
                .flatMap(anonymizedDiscoveredAddressId -> PaperProgressStatusEventMapper.toPaperTrackings(context.getPaperProgressStatusEvent(), context.getAnonymizedDiscoveredAddressId(), context.getEventId()))
                .flatMap(paperTrackings -> paperTrackingsDAO.updateItem(context.getPaperProgressStatusEvent().getRequestId(), paperTrackings))
                .doOnNext(paperTrackings -> {
                    context.setPaperTrackings(paperTrackings);
                    context.setTrackingId(paperTrackings.getTrackingId());
                })
                .then();
    }

    private Mono<HandlerContext> discoveredAddressAnonimization(HandlerContext handlerContext) {
        DiscoveredAddress discoveredAddress = handlerContext.getPaperProgressStatusEvent().getDiscoveredAddress();
        if (Objects.nonNull(discoveredAddress)) {
            return dataVaultClient.anonymizeDiscoveredAddress(handlerContext.getPaperProgressStatusEvent().getRequestId(), discoveredAddress)
                    .doOnNext(handlerContext::setAnonymizedDiscoveredAddressId)
                    .thenReturn(handlerContext);
        }
        return Mono.just(handlerContext);
    }
}