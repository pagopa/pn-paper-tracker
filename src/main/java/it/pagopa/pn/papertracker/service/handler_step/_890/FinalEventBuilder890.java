package it.pagopa.pn.papertracker.service.handler_step._890;

import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.mapper.PaperTrackingsErrorsMapper;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.middleware.msclient.DataVaultClient;
import it.pagopa.pn.papertracker.model.EventStatus;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import it.pagopa.pn.papertracker.service.handler_step.generic.GenericFinalEventBuilder;
import it.pagopa.pn.papertracker.utils.TrackerUtility;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import static it.pagopa.pn.papertracker.model.EventStatusCodeEnum.*;

@Component
@Slf4j
public class FinalEventBuilder890 extends GenericFinalEventBuilder implements HandlerStep {

    public FinalEventBuilder890(DataVaultClient dataVaultClient, PaperTrackingsDAO paperTrackingsDAO) {
        super(dataVaultClient, paperTrackingsDAO);
    }

    /**
     * Step che elabora l'evento finale (890) in base alla logica di business definita.
     * Se l'evento finale non è uno stato di giacenza, viene semplicemente aggiunto alla lista degli eventi da inviare.
     * Se l'evento finale è uno stato di giacenza e la notifica è stata perfezionata, viene aggiunto alla
     * lista degli eventi da inviare, altrimenti viene lanciato un errore e salvato sulla tabella degli errori.
     * @param context Contesto contenente le informazioni necessarie per l'elaborazione dell'evento.
     * @return Mono(Void)
     */
    @Override
    public Mono<Void> execute(HandlerContext context) {
        log.info("FinalEventBuilder890 execute for trackingId: {}", context.getTrackingId());

        Event finalEvent = extractFinalEvent(context);
        String statusCode = finalEvent.getStatusCode();
        if (TrackerUtility.isStockStatus890(statusCode))
            return context.getPaperTrackings().isRefined() ?
                    addEventToSend(context, finalEvent, EventStatus.PROGRESS.name()) :
                    buildError(context, finalEvent);

        String eventStatus = evaluateStatusCodeAndRetrieveStatus(RECAG003C.name(), statusCode, context.getPaperTrackings()).name();
        return addEventToSend(context, finalEvent, eventStatus);
    }

    private Mono<Void> buildError(HandlerContext context, Event finalEvent) {
        log.error("Delivery 890 in stock not completed for trackingId: {}", context.getTrackingId());
        return Mono.error(new PnPaperTrackerValidationException(
                "Spedizione 890 non perfezionata in giacenza",
                PaperTrackingsErrorsMapper.buildPaperTrackingsError(
                        context.getPaperTrackings(),
                        finalEvent.getStatusCode(),
                        ErrorCategory.RENDICONTAZIONE_SCARTATA,
                        ErrorCause.GIACENZA_RECAG012_ERROR,
                        "Spedizione 890 non perfezionata in giacenza",
                        FlowThrow.FINAL_EVENT_BUILDING,
                        ErrorType.ERROR,
                        finalEvent.getId()
                )));
    }

}
