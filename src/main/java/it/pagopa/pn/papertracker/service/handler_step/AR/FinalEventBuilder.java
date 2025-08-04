package it.pagopa.pn.papertracker.service.handler_step.AR;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.config.StatusCodeConfiguration;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SendEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.StatusCodeEnum;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import it.pagopa.pn.papertracker.service.mapper.SendEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static it.pagopa.pn.papertracker.config.StatusCodeConfiguration.StatusCodeConfigurationEnum.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class FinalEventBuilder implements HandlerStep {

    private final PnPaperTrackerConfigs pnPaperTrackerConfigs;
    private final StatusCodeConfiguration statusCodeConfiguration;

    @Override
    public Mono<Void> execute(HandlerContext context) {
        return buildFinalEvent(context.getPaperTrackings(), context.getPaperProgressStatusEvent(), context)
                .then();
    }


    public Mono<Void> buildFinalEvent(PaperTrackings paperTrackings, PaperProgressStatusEvent finalEvent, HandlerContext handlerContext) {
        String statusCode = finalEvent.getStatusCode();
        log.info("Building final event for statusCode: {}", statusCode);

        if (!isStockStatus(statusCode)) {
            setSingleEventToSend(finalEvent, handlerContext);
            return Mono.empty();
        }

        List<Event> validatedEvents = paperTrackings.getNotificationState().getValidatedEvents();
        StatusCodeConfiguration.StatusCodeConfigurationEnum configEnum = getRECRN00XA(statusCode);
        Event eventRECRN00XA = getEvent(validatedEvents, configEnum);
        Event eventRECRN010 = getEvent(validatedEvents, RECRN010);

        return Mono.just(isDifferenceGreater(statusCode, eventRECRN010, eventRECRN00XA))
                .flatMap(isGreater -> {
                    if (isGreater) {
                        return prepareFinalEventAndPNRN012toSend(finalEvent, eventRECRN010);
                    }

                    if (RECRN005C.name().equals(statusCode)) {
                        log.error("The difference between RECRN005A and RECRN010 is greater than the configured duration, throwing exception");
                        return Mono.error(new PnPaperTrackerValidationException(
                                "The difference between RECRN005A and RECRN010 is greater than the configured duration",
                                getPaperTrackingsErrors(paperTrackings, finalEvent, eventRECRN00XA, eventRECRN010)
                        ));
                    }

                    setSingleEventToSend(finalEvent, handlerContext);
                    return Mono.empty();
                });
    }

    private void setSingleEventToSend(PaperProgressStatusEvent finalEvent, HandlerContext handlerContext) {
        handlerContext.setEventsToSend(List.of(
                SendEventMapper.toSendEvent(
                        finalEvent,
                        StatusCodeEnum.valueOf(getStatusCode(finalEvent)),
                        finalEvent.getStatusCode(),
                        finalEvent.getStatusDateTime()
                )
        ));
    }

    private boolean isDifferenceGreater(String statusCode, Event eventRECRN010, Event eventRECRN00XA) {
        return RECRN005C.name().equals(statusCode)
                ? isDifferenceGreaterOrEqualToStockDuration(eventRECRN010.getStatusTimestamp(), eventRECRN00XA.getStatusTimestamp())
                : isDifferenceGreaterRefinementDuration(eventRECRN010.getStatusTimestamp(), eventRECRN00XA.getStatusTimestamp());
    }

    private StatusCodeConfiguration.StatusCodeConfigurationEnum getRECRN00XA(String statusCode) {
        final String status = statusCode.substring(0, statusCode.length() - 1).concat("A");
        return StatusCodeConfiguration.StatusCodeConfigurationEnum.fromKey(status);
    }

    private boolean isStockStatus(String status) {
        return RECRN003C.name().equals(status) ||
                RECRN004C.name().equals(status) ||
                RECRN005C.name().equals(status);
    }

    private Mono<Void> prepareFinalEventAndPNRN012toSend(PaperProgressStatusEvent finalEvent, Event eventRECRN010, HandlerContext handlerContext){
        OffsetDateTime statusDateTime = OffsetDateTime.ofInstant(addDurationToInstant(eventRECRN010.getStatusTimestamp(), pnPaperTrackerConfigs.getRefinementDuration()), ZoneOffset.UTC);
        SendEvent eventPNRN012 = SendEventMapper.toSendEvent(finalEvent, StatusCodeEnum.OK, PNRN012.name(), statusDateTime);
        SendEvent finalEventToSend = SendEventMapper.toSendEvent(finalEvent, StatusCodeEnum.PROGRESS, finalEvent.getStatusCode(), finalEvent.getStatusDateTime());
        handlerContext.setEventsToSend(List.of(eventPNRN012, finalEventToSend));
        return Mono.empty();
    }

    private String getStatusCode(PaperProgressStatusEvent finalEvent) {
        return statusCodeConfiguration.getStatusFromStatusCode(finalEvent.getStatusCode()).name();
    }

    private PaperTrackingsErrors getPaperTrackingsErrors(PaperTrackings paperTrackings, PaperProgressStatusEvent finalEvent,
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
        errors.setProductType(ProductType.valueOf(finalEvent.getProductType()));
        errors.setType(ErrorType.ERROR);
        return errors;
    }

    private Duration getDurationBetweenDates(Instant instant1, Instant instant2) {
        return pnPaperTrackerConfigs.isEnableTruncatedDateForRefinementCheck()
                ? Duration.ofDays(Math.abs(ChronoUnit.DAYS.between(toRomeDate(instant1), toRomeDate(instant2))))
                : Duration.between(instant1, instant2);
    }

    private boolean isDifferenceGreaterRefinementDuration(Instant recrn010Timestamp, Instant recrn00xATimestamp) {
        log.debug("recrn010Timestamp={}, recrn00xATimestamp={}, refinementDuration={}", recrn010Timestamp, recrn00xATimestamp, pnPaperTrackerConfigs.getRefinementDuration());
        return getDurationBetweenDates(recrn010Timestamp, recrn00xATimestamp)
                .compareTo(pnPaperTrackerConfigs.getRefinementDuration()) > 0;
    }

    private boolean isDifferenceGreaterOrEqualToStockDuration(Instant recrn010Timestamp, Instant recrn005ATimestamp) {
        log.debug("recrn010Timestamp={}, recrn005ATimestamp={}, refinementDuration={}", recrn010Timestamp, recrn005ATimestamp, pnPaperTrackerConfigs.getCompiutaGiacenzaArDuration());
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
