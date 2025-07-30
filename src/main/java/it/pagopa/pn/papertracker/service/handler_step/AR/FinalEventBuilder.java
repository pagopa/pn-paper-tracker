package it.pagopa.pn.papertracker.service.handler_step.AR;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.config.StatusCodeConfiguration;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.model.EventStatus;
import it.pagopa.pn.papertracker.model.HandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static it.pagopa.pn.papertracker.config.StatusCodeConfiguration.StatusCodeConfigurationEnum.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class FinalEventBuilder {

    private final PnPaperTrackerConfigs pnPaperTrackerConfigs;
    private final HandlerContext handlerContext;

    public Mono<Void> buildFinalEvent(PaperTrackings paperTrackings, Event finalEvent) {
        String statusCode = finalEvent.getStatusCode();
        log.info("Building final event for statusCode: {}", statusCode);

        if (isStockStatus(statusCode)) {
            List<Event> validatedEvents = paperTrackings.getNotificationState().getValidatedEvents();
            StatusCodeConfiguration.StatusCodeConfigurationEnum RECRN00XA = getRECRN00XA(statusCode);
            Event eventRECRN00XA = getEvent(validatedEvents, RECRN00XA);
            Event eventRECRN010 = getEvent(validatedEvents, RECRN010);

            return Mono.just(isDifferenceGreater(statusCode, eventRECRN010, eventRECRN00XA))
                    .flatMap(isDifferenceGreater -> {
                        if (isDifferenceGreater) {
                            return prepareFinalEventAndPNRN012toSend(paperTrackings, finalEvent, eventRECRN010);
                        }
                        if (RECRN005C.name().equals(statusCode)) {
                            log.error("The difference between RECRN005A and RECRN010 is greater than the configured duration, throwing PnPaperTrackerValidationException");
                            return Mono.error(new PnPaperTrackerValidationException(
                                    "The difference between RECRN005A and RECRN010 is greater than the configured duration", getPaperTrackingsErrors(paperTrackings, finalEvent, eventRECRN00XA, eventRECRN010)
                            ));
                        } else {
                            return prepareToSend(paperTrackings, List.of(finalEvent));
                        }
                    });
        } else {
            return prepareToSend(paperTrackings, List.of(finalEvent));
        }
    }

    private boolean isDifferenceGreater(String statusCode, Event eventRECRN010, Event eventRECRN00XA) {
        return RECRN005C.name().equals(statusCode)
                ? isDifferenceGreaterOrEqualToStockDuration(eventRECRN010.getStatusTimestamp(), eventRECRN00XA.getStatusTimestamp())
                : isDifferenceGreaterRefinementDuration(eventRECRN010.getStatusTimestamp(), eventRECRN00XA.getStatusTimestamp());
    }

    private Mono<Void> prepareToSend(PaperTrackings paperTrackings, List<Event> eventsToSend) {
        handlerContext.setPaperTrackingsToSend(paperTrackings);
        handlerContext.setEventsToSend(eventsToSend);
        return Mono.empty();
    }

    private StatusCodeConfiguration.StatusCodeConfigurationEnum getRECRN00XA(String statusCode) {
        final String status = statusCode.substring(0, statusCode.length() - 1)
                .concat("A");
        return StatusCodeConfiguration.StatusCodeConfigurationEnum.fromKey(status);
    }

    private boolean isStockStatus(String status) {
        return RECRN003C.name().equals(status) ||
                RECRN004C.name().equals(status) ||
                RECRN005C.name().equals(status);
    }

    private Mono<Void> prepareFinalEventAndPNRN012toSend(PaperTrackings paperTrackings, Event finalEvent, Event eventRECRN010) {
        Event eventPNRN012 = getEventPNRN012(finalEvent, eventRECRN010);
        finalEvent.setEventStatus(EventStatus.PROGRESS);
        return prepareToSend(paperTrackings, List.of(eventPNRN012, finalEvent));
    }

    private Event getEventPNRN012(Event finalEvent, Event eventRECRN010) {
        Event eventPNRN012 = new Event();
        eventPNRN012.setStatusCode(PNRN012.name());
        eventPNRN012.setRequestTimestamp(Instant.now());
        eventPNRN012.setStatusTimestamp(addDurationToInstant(eventRECRN010.getStatusTimestamp(), pnPaperTrackerConfigs.getRefinementDuration()));
        eventPNRN012.setProductType(finalEvent.getProductType());
        eventPNRN012.setAttachments(finalEvent.getAttachments());
        eventPNRN012.setDeliveryFailureCause(finalEvent.getDeliveryFailureCause());
        return eventPNRN012;
    }

    private PaperTrackingsErrors getPaperTrackingsErrors(PaperTrackings paperTrackings, Event finalEvent,
                                                         Event RECRN00XA, Event RECRN010) {
        PaperTrackingsErrors errors = new PaperTrackingsErrors();
        errors.setRequestId(paperTrackings.getRequestId());
        errors.setCreated(Instant.now());
        errors.setErrorCategory(ErrorCategory.RENDICONTAZIONE_SCARTATA);
        ErrorDetails errorDetails = new ErrorDetails();
        errorDetails.setMessage(String.format("RECRN005A getStatusTimestamp: %s, RECRN010 getStatusTimestamp: %s", RECRN00XA.getStatusTimestamp(), RECRN010.getStatusTimestamp()));
        errorDetails.setCause(ErrorCause.GIACENZA_DATE_ERROR);
        errors.setDetails(errorDetails);
        errors.setFlowThrow(FlowThrow.FINAL_EVENT_BUILDING);
        errors.setEventThrow(finalEvent.getStatusCode());
        errors.setProductType(finalEvent.getProductType());
        errors.setType(ErrorType.ERROR);
        return errors;
    }

    private Duration getDurationBetweenDates(Instant instant1, Instant instant2) {
        return pnPaperTrackerConfigs.isEnableTruncatedDateForRefinementCheck()
                ? Duration.ofDays(
                Math.abs(ChronoUnit.DAYS.between(toRomeDate(instant1), toRomeDate(instant2))))
                : Duration.between(instant1, instant2);
    }

    private boolean isDifferenceGreaterRefinementDuration(
            Instant recrn010Timestamp,
            Instant recrn00xATimestamp) {
        log.debug("recrn010Timestamp={}, recrn00xATimestamp={}, refinementDuration={}",
                recrn010Timestamp, recrn00xATimestamp, pnPaperTrackerConfigs.getRefinementDuration());
        return getDurationBetweenDates(recrn010Timestamp, recrn00xATimestamp)
                .compareTo(pnPaperTrackerConfigs.getRefinementDuration()) > 0;
    }

    private boolean isDifferenceGreaterOrEqualToStockDuration(
            Instant recrn010Timestamp,
            Instant recrn005ATimestamp) {
        log.debug("recrn010Timestamp={}, recrn005ATimestamp={}, refinementDuration={}",
                recrn010Timestamp, recrn005ATimestamp, pnPaperTrackerConfigs.getCompiutaGiacenzaArDuration());
        return getDurationBetweenDates(recrn010Timestamp, recrn005ATimestamp)
                .compareTo(pnPaperTrackerConfigs.getCompiutaGiacenzaArDuration()) >= 0;
    }

    private Event getEvent(List<Event> events, StatusCodeConfiguration.StatusCodeConfigurationEnum code) {
        return events.stream()
                .filter(e -> code.name().equals(e.getStatusCode()))
                .findFirst()
                .orElseThrow();
    }

    private LocalDate toRomeDate(Instant instant) {
        return instant.atZone(ZoneId.of("Europe/Rome"))
                .toLocalDate();
    }

    private Instant romeDateToInstant(LocalDate date) {
        ZoneId rome = ZoneId.of("Europe/Rome");
        return date.atStartOfDay(rome)
                .toInstant();
    }

    private Instant addDurationToInstant(Instant instant, Duration duration) {
        return pnPaperTrackerConfigs.isEnableTruncatedDateForRefinementCheck()
                ? romeDateToInstant(
                toRomeDate(instant).plusDays(duration.toDays()))
                : instant.plus(duration);
    }
}
