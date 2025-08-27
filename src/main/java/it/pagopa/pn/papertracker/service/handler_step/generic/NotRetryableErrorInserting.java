package it.pagopa.pn.papertracker.service.handler_step.generic;

import it.pagopa.pn.papertracker.config.StatusCodeConfiguration;
import it.pagopa.pn.papertracker.exception.PaperTrackerExceptionHandler;
import it.pagopa.pn.papertracker.mapper.PaperTrackingsErrorsMapper;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotRetryableErrorInserting implements HandlerStep {

    private final PaperTrackerExceptionHandler paperTrackerExceptionHandler;

    @Override
    public Mono<Void> execute(HandlerContext context) {
        String statusCode = context.getPaperProgressStatusEvent().getStatusCode();
        PaperTrackingsErrors paperTrackingsErrors = PaperTrackingsErrorsMapper.buildPaperTrackingsError(context.getPaperTrackings(),
                List.of(statusCode),
                ErrorCategory.NOT_RETRYABLE_EVENT_HANDLING,
                null,
                StatusCodeConfiguration.StatusCodeConfigurationEnum.fromKey(statusCode).getStatusCodeDescription(),
                FlowThrow.NOT_RETRYABLE_EVENT_HANDLING,
                ErrorType.WARNING
        );
        return paperTrackerExceptionHandler.handleRetryError(paperTrackingsErrors);
    }
}
