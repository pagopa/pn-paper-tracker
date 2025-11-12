package it.pagopa.pn.papertracker.service.handler_step.generic;

import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.mapper.PaperTrackingsErrorsMapper;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import static it.pagopa.pn.papertracker.model.EventStatusCodeEnum.RECAG012;

@Component
@RequiredArgsConstructor
@CustomLog
public class CheckTrackingState implements HandlerStep {

    /**
     * Step che effettua un controllo se lo stato del tracking associato all'evento ricevuto
     * è in stato DONE o AWAITING_OCR.
     * Vengono esclusi da questo controllo gli eventi con statusCode che iniziano con "CON".
     * Se il tracking è in uno di questi stati, viene generato un errore di validazione,
     * altrimenti, il flusso prosegue con gli step successivi.
     *
     * @param context Contesto contenente le informazioni necessarie per l'elaborazione dell'evento.
     * @return Mono(Void)
     */
    public Mono<Void> execute(HandlerContext context) {
        return Mono.just(context)
                .flatMap(ctx -> {
                    String statusCode = ctx.getPaperProgressStatusEvent().getStatusCode();
                    if (statusCode.startsWith("CON")) {
                        log.info("StatusCode excluded from CheckTrackingState: {}", statusCode);
                        return Mono.empty();
                    }

                    return isInvalidState(ctx, statusCode)
                            ? createValidationError(ctx, statusCode)
                            : Mono.empty();
                });
    }

    private boolean isInvalidState(HandlerContext ctx, String statusCode) {
        if (statusCode.equalsIgnoreCase(RECAG012.name())) {
            PaperTrackingsState state = ctx.getPaperTrackings().getState();
            return state == PaperTrackingsState.DONE || state == PaperTrackingsState.AWAITING_OCR;
        } else {
            BusinessState businessState = ctx.getPaperTrackings().getBusinessState();
            return businessState == BusinessState.DONE || businessState == BusinessState.AWAITING_OCR;
        }
    }

    private Mono<Void> createValidationError(HandlerContext ctx, String statusCode) {
        String state = statusCode.equalsIgnoreCase(RECAG012.name())
                ? ctx.getPaperTrackings().getState().name()
                : ctx.getPaperTrackings().getBusinessState().name();

        String errorMsg = String.format("Tracking in state %s: %s", state, ctx.getTrackingId());

        return Mono.error(new PnPaperTrackerValidationException(
                errorMsg,
                PaperTrackingsErrorsMapper.buildPaperTrackingsError(
                        ctx.getPaperTrackings(),
                        statusCode,
                        ErrorCategory.INCONSISTENT_STATE,
                        null,
                        errorMsg,
                        FlowThrow.CHECK_TRACKING_STATE,
                        ErrorType.WARNING,
                        ctx.getEventId()
                )
        ));
    }
}
