package it.pagopa.pn.papertracker.service.impl;

import it.pagopa.pn.papertracker.config.SequenceConfiguration;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerBadRequestException;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.SequenceResponse;
import it.pagopa.pn.papertracker.model.EventStatus;
import it.pagopa.pn.papertracker.model.SequenceElement;
import it.pagopa.pn.papertracker.service.NotificationReworkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static it.pagopa.pn.papertracker.utils.TrackerUtility.evaluateStatusCodeAndRetrieveStatus;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationReworkServiceImpl implements NotificationReworkService {
    private static final String ERROR_CODE_PAPER_TRACKER_BAD_REQUEST = "PN_PAPER_TRACKER_BAD_REQUEST";

    @Override
    public Mono<SequenceResponse> notificationRework(String statusCode, String deliveryFailureCause) {
        SequenceResponse sequenceResponse = new SequenceResponse();

        return Mono.justOrEmpty(evaluateStatusCodeAndRetrieveStatus(statusCode, deliveryFailureCause))
                .switchIfEmpty(Mono.error(new PnPaperTrackerBadRequestException(ERROR_CODE_PAPER_TRACKER_BAD_REQUEST, String.format("statusCode %s is invalid", statusCode))))
                .filter(eventStatus -> !EventStatus.PROGRESS.equals(eventStatus))
                .switchIfEmpty(Mono.error(new PnPaperTrackerBadRequestException(ERROR_CODE_PAPER_TRACKER_BAD_REQUEST, String.format("statusCode %s is PROGRESS", statusCode))))
                .doOnNext(eventStatus -> sequenceResponse.setFinalStatusCode(SequenceResponse.FinalStatusCodeEnum.fromValue(eventStatus.name())))
                .flatMap(eventStatus ->
                        Mono.fromCallable(() -> SequenceConfiguration.SequenceDefinition.fromKey(statusCode)
                                        .getSequence()
                                        .stream()
                                        .map(SequenceElement::getCode)
                                        .toList())
                                .onErrorMap(IllegalArgumentException.class, e -> new PnPaperTrackerBadRequestException(ERROR_CODE_PAPER_TRACKER_BAD_REQUEST, String.format("statusCode %s doesn't exist", statusCode))))
                .map(sequenceList -> {
                    sequenceResponse.setSequence(sequenceList);
                    log.info("Successfully retrieved sequence for statusCode {} with {} elements", statusCode, sequenceList.size());
                    return sequenceResponse;
                });
    }
}


