package it.pagopa.pn.papertracker.service.handler_step.generic;

import it.pagopa.pn.papertracker.middleware.msclient.PaperChannelClient;
import it.pagopa.pn.papertracker.model.DeliveryFailureCauseEnum;
import it.pagopa.pn.papertracker.model.HandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Attiva il retry verso pn-paper-channel solo quando il tracking presenta la
 * delivery failure cause {@code M10}.
 */
@Component
@Slf4j
public class M10RetryTrigger extends RetrySender {

    public M10RetryTrigger(PaperChannelClient paperChannelClient, PcRetryService pcRetryService) {
        super(paperChannelClient, pcRetryService);
    }

    /**
     * Verifica la causale di mancata consegna del tracking e, se corrisponde a
     * {@code M10}, esegue il retry ereditato da {@link RetrySender}.
     *
     * @param context contesto contenente il tracking da verificare
     * @return mono vuoto se la causale non è {@code M10}, altrimenti il mono del retry
     */
    @Override
    public Mono<Void> execute(HandlerContext context) {
        boolean hasM10FailureCause = context.getPaperTrackings()
                                            .getPaperStatus()
                                            .getDeliveryFailureCause()
                                            .equalsIgnoreCase(DeliveryFailureCauseEnum.M10.name());

        if (!hasM10FailureCause) {
            return Mono.empty();
        }

        log.info("DeliveryFailureCause M10 found for trackingId: {}, executing PcRetryService", context.getTrackingId());
        return super.execute(context);
    }
}
