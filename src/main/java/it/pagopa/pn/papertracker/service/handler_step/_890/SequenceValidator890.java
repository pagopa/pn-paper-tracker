package it.pagopa.pn.papertracker.service.handler_step._890;

import it.pagopa.pn.papertracker.model.sequence.SequenceConfig;
import it.pagopa.pn.papertracker.model.sequence.SequenceConfiguration;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.mapper.PaperTrackingsErrorsMapper;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import it.pagopa.pn.papertracker.service.handler_step.generic.GenericSequenceValidator;
import it.pagopa.pn.papertracker.utils.TrackerUtility;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class SequenceValidator890 extends GenericSequenceValidator implements HandlerStep {

    private final PaperTrackingsDAO paperTrackingsDAO;

    public SequenceValidator890(PaperTrackingsDAO paperTrackingsDAO) {
        super(paperTrackingsDAO);
        this.paperTrackingsDAO = paperTrackingsDAO;
    }

    @Override
    public Mono<Void> execute(HandlerContext context) {
        log.info("SequenceValidator890 execute for trackingId: {}", context.getTrackingId());
        SequenceConfig sequenceConfig = SequenceConfiguration.getConfig(context.getPaperProgressStatusEvent().getStatusCode());

        return Mono.just(context.getPaperTrackings())
                .filter(paperTrackings -> checkState(context))
                .flatMap(paperTrackings -> validateSequence(paperTrackings, context, sequenceConfig))
                .doOnNext(context::setPaperTrackings)
                .then();
    }

    private boolean checkState(HandlerContext context) {
        if (!TrackerUtility.isStockStatus890(context.getPaperProgressStatusEvent().getStatusCode()))
            return true;

        PaperTrackingsState state = context.getPaperTrackings().getState();
        log.info("Current state for trackingId {}: {}", context.getTrackingId(), state);
        //TODO: GESTIRE LA NUOVA ENV QUANDO PRESENTE PER STRICT FINAL EVENT VALIDATION PROCESS
        return switch (state) {
            case DONE -> true;
            case AWAITING_REFINEMENT -> throw new PnPaperTrackerValidationException(
                    "invalid AWAITING_REFINEMENT state for stock 890",
                    PaperTrackingsErrorsMapper.buildPaperTrackingsError(
                            context.getPaperTrackings(),
                            context.getPaperProgressStatusEvent().getStatusCode(),
                            ErrorCategory.INCONSISTENT_STATE,
                            ErrorCause.STOCK_890_REFINEMENT_MISSING,
                            "invalid AWAITING_REFINEMENT state for stock 890",
                            FlowThrow.SEQUENCE_VALIDATION,
                            ErrorType.ERROR,
                            context.getEventId()
                    )
            );
            case AWAITING_OCR -> {
                log.info("Awaiting OCR response for refinement, updating business state to AWAITING_REFINEMENT_OCR for trackingId: {}", context.getTrackingId());
                paperTrackingsDAO.updateItem(context.getTrackingId(), getPaperTrackingsToUpdate());
                context.setStopExecution(true);
                yield false;
            }
            case KO -> throw new PnPaperTrackerValidationException(
                    "Refinement process reached KO state, cannot proceed with final event validation",
                    PaperTrackingsErrorsMapper.buildPaperTrackingsError(
                            context.getPaperTrackings(),
                            context.getPaperProgressStatusEvent().getStatusCode(),
                            ErrorCategory.INCONSISTENT_STATE,
                            ErrorCause.STOCK_890_REFINEMENT_ERROR,
                            "Refinement process reached KO state, cannot proceed with final event validation",
                            FlowThrow.SEQUENCE_VALIDATION,
                            ErrorType.ERROR,
                            context.getEventId()
                    )
            );
        };
    }

    private PaperTrackings getPaperTrackingsToUpdate() {
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setBusinessState(BusinessState.AWAITING_REFINEMENT_OCR);
        return paperTrackings;
    }
}
