package it.pagopa.pn.papertracker.service.handler_step._890;

import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsErrorsDAO;
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
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;
import java.util.Objects;

@Component
@Slf4j
public class SequenceValidator890 extends GenericSequenceValidator implements HandlerStep {

    private final PaperTrackingsDAO paperTrackingsDAO;

    public SequenceValidator890(PaperTrackingsDAO paperTrackingsDAO, PaperTrackingsErrorsDAO paperTrackingsErrorsDAO) {
        super(paperTrackingsDAO, paperTrackingsErrorsDAO);
        this.paperTrackingsDAO = paperTrackingsDAO;
    }

    @Override
    public Mono<Void> execute(HandlerContext context) {
        log.info("SequenceValidator890 execute for trackingId: {}", context.getTrackingId());
        SequenceConfig sequenceConfig;
        boolean isStock890 = TrackerUtility.isStockStatus890(context.getPaperProgressStatusEvent().getStatusCode());
        Boolean strictFinalValidationStock890 = context.getPaperTrackings().getValidationConfig().getStrictFinalValidationStock890();

        if(Objects.nonNull(context.getPaperProgressStatusEvent())){
            sequenceConfig = SequenceConfiguration.getConfig(context.getPaperProgressStatusEvent().getStatusCode());
        }else{
            String finalEventStatusCode = TrackerUtility.getStatusCodeFromEventId(context.getPaperTrackings(), context.getEventId());
            sequenceConfig = SequenceConfiguration.getConfig(finalEventStatusCode);
        }
        return Mono.just(context.getPaperTrackings())
                .filter(paperTrackings -> !isStock890 || checkState(context))
                .flatMap(paperTrackings -> validateSequence(paperTrackings, context, sequenceConfig,
                        isStock890 ? strictFinalValidationStock890 : true))
                .doOnNext(context::setPaperTrackings)
                .then();
    }

    private boolean checkState(HandlerContext context) {
        PaperTrackingsState state = context.getPaperTrackings().getState();
        log.info("Current state for trackingId {}: {}", context.getTrackingId(), state);
        return switch (state) {
            case DONE -> true;
            case AWAITING_REFINEMENT, AWAITING_REWORK_EVENTS -> throw new PnPaperTrackerValidationException(
                    "invalid AWAITING_REFINEMENT state for stock 890",
                    PaperTrackingsErrorsMapper.buildPaperTrackingsError(
                            context.getPaperTrackings(),
                            context.getPaperProgressStatusEvent().getStatusCode(),
                            ErrorCategory.INCONSISTENT_STATE,
                            ErrorCause.STOCK_890_REFINEMENT_MISSING,
                            "invalid AWAITING_REFINEMENT state for stock 890",
                            Map.of("statusCode", AttributeValue.builder().s(context.getPaperProgressStatusEvent().getStatusCode()).build(),
                                "statusTimestamp", AttributeValue.builder().s(context.getPaperProgressStatusEvent().getStatusDateTime().toString()).build()
                            ),
                            FlowThrow.SEQUENCE_VALIDATION,
                            ErrorType.ERROR,
                            context.getEventId()
                    )
            );
            case AWAITING_OCR -> {
                log.info("Awaiting OCR response for refinement, updating business state to AWAITING_REFINEMENT_OCR for trackingId: {}", context.getTrackingId());
                paperTrackingsDAO.updateItem(context.getTrackingId(), getPaperTrackingsToUpdate(context.getEventId()));
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
                            Map.of("statusCode", AttributeValue.builder().s(context.getPaperProgressStatusEvent().getStatusCode()).build(),
                                    "statusTimestamp", AttributeValue.builder().s(context.getPaperProgressStatusEvent().getStatusDateTime().toString()).build()
                            ),
                            FlowThrow.SEQUENCE_VALIDATION,
                            ErrorType.ERROR,
                            context.getEventId()
                    )
            );
            case AWAITING_FINAL_STATUS_CODE -> throw new PnPaperTrackerValidationException(
                    "Invalid state for processing stock 890 final event",
                    PaperTrackingsErrorsMapper.buildPaperTrackingsError(
                            context.getPaperTrackings(),
                            context.getPaperProgressStatusEvent().getStatusCode(),
                            ErrorCategory.INVALID_STATE_FOR_STOCK_890,
                            ErrorCause.STOCK_890_REFINEMENT_ERROR,
                            String.format("Invalid state %s for processing stock 890 final event",state),
                            null,
                            FlowThrow.SEQUENCE_VALIDATION,
                            ErrorType.ERROR,
                            context.getEventId()
                    ));
        };
    }

    private PaperTrackings getPaperTrackingsToUpdate(String eventId) {
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setBusinessState(BusinessState.AWAITING_REFINEMENT_OCR);
        paperTrackings.setPendingFinalEventId(eventId);
        return paperTrackings;
    }
}
