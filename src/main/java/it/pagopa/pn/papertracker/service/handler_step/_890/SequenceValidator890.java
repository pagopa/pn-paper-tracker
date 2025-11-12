package it.pagopa.pn.papertracker.service.handler_step._890;

import it.pagopa.pn.papertracker.config.SequenceConfiguration;
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

    public SequenceValidator890(SequenceConfiguration sequenceConfiguration, PaperTrackingsDAO paperTrackingsDAO) {
        super(sequenceConfiguration, paperTrackingsDAO);
        this.paperTrackingsDAO = paperTrackingsDAO;
    }

    @Override
    public Mono<Void> execute(HandlerContext context) {
        log.info("SequenceValidator890 execute for trackingId: {}", context.getTrackingId());

        return Mono.just(context.getPaperTrackings())
                .filter(paperTrackings -> checkState(context))
                .flatMap(paperTrackings -> validateSequence(paperTrackings, context))
                .flatMap(updatedPaperTracking -> {
                    context.setPaperTrackings(updatedPaperTracking);
                    return Mono.empty();
                });
    }

    private boolean checkState(HandlerContext context) {
        if (!TrackerUtility.isStockStatus890(context.getPaperProgressStatusEvent().getStatusCode()))
            return true;

        PaperTrackingsState state = context.getPaperTrackings().getState();
        log.info("Current state for trackingId {}: {}", context.getTrackingId(), state);

        return switch (state) {
            case DONE -> true;
            case AWAITING_REFINEMENT, AWAITING_FINAL_STATUS_CODE -> throw new PnPaperTrackerValidationException(
                    "AWAITING_REFINEMENT state reached",
                    PaperTrackingsErrorsMapper.buildPaperTrackingsError(
                            context.getPaperTrackings(),
                            context.getPaperProgressStatusEvent().getStatusCode(),
                            ErrorCategory.STATUS_CODE_ERROR,
                            ErrorCause.OCR_KO,
                            "AWAITING_REFINEMENT state reached",
                            FlowThrow.SEQUENCE_VALIDATION,
                            ErrorType.ERROR,
                            context.getEventId()
                    )
            );
            case AWAITING_OCR -> {
                paperTrackingsDAO.updateItem(context.getTrackingId(), getPaperTrackingsToUpdate());
                context.setStopExecution(true);
                yield false;
            }
            case KO -> throw new PnPaperTrackerValidationException(
                    "KO state reached",
                    PaperTrackingsErrorsMapper.buildPaperTrackingsError(
                            context.getPaperTrackings(),
                            context.getPaperProgressStatusEvent().getStatusCode(),
                            ErrorCategory.STATUS_CODE_ERROR,
                            ErrorCause.OCR_KO,
                            "KO state reached",
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
