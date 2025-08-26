package it.pagopa.pn.papertracker.service.handler_step.AR;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.config.StatusCodeConfiguration;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.StatusCodeEnum;
import it.pagopa.pn.papertracker.mapper.PaperTrackingsErrorsMapper;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.middleware.msclient.DataVaultClient;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.GenericFinalEventBuilder;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static it.pagopa.pn.papertracker.config.StatusCodeConfiguration.StatusCodeConfigurationEnum.*;

@Component
@Slf4j
public class FinalEventBuilderAr extends GenericFinalEventBuilder implements HandlerStep {

    private final PnPaperTrackerConfigs pnPaperTrackerConfigs;
    private final PaperTrackingsDAO paperTrackingsDAO;

    public FinalEventBuilderAr(PnPaperTrackerConfigs pnPaperTrackerConfigs, DataVaultClient dataVaultClient, PaperTrackingsDAO paperTrackingsDAO) {
        super(dataVaultClient, paperTrackingsDAO);
        this.pnPaperTrackerConfigs = pnPaperTrackerConfigs;
        this.paperTrackingsDAO = paperTrackingsDAO;
    }

    @Override
    public Mono<Void> execute(HandlerContext context) {
        return Mono.just(extractFinalEvent(context))
                .doOnNext(event -> context.setFinalStatusCode(event.getStatusCode()))
                .flatMap(event -> handleFinalEvent(context, event))
                .thenReturn(context)
                .map(ctx -> paperTrackingsDAO.updateItem(ctx.getPaperTrackings().getTrackingId(), getPaperTrackingsToUpdate()))
                .then();
    }

    private Mono<Void> handleFinalEvent(HandlerContext context, Event finalEvent) {
        PaperTrackings paperTrackings = context.getPaperTrackings();
        String statusCode = finalEvent.getStatusCode();
        if (!isStockStatus(statusCode)) {
            return addEventToSend(context, finalEvent, StatusCodeConfiguration.StatusCodeConfigurationEnum.fromKey(statusCode).getStatus().name());
        }

        List<Event> validatedEvents = paperTrackings.getPaperStatus().getValidatedEvents();
        StatusCodeConfiguration.StatusCodeConfigurationEnum configEnum = getRECRN00XA(statusCode);
        Event eventRECRN00XA = getEvent(validatedEvents, configEnum);
        Event eventRECRN010 = getEvent(validatedEvents, RECRN010);

        boolean differenceGreater = isDifferenceGreater(statusCode, eventRECRN010, eventRECRN00XA);
        return differenceGreater
                ? prepareFinalEventAndPNRN012toSend(context, finalEvent, eventRECRN010)
                : handleNoDifferenceGreater(context, paperTrackings, statusCode, finalEvent, eventRECRN00XA, eventRECRN010);
    }

    private Mono<Void> handleNoDifferenceGreater(HandlerContext context,
                                                 PaperTrackings paperTrackings,
                                                 String statusCode,
                                                 Event finalEvent,
                                                 Event eventRECRN00XA,
                                                 Event eventRECRN010) {
        if (RECRN005C.name().equals(statusCode)) {
            log.error("The difference between RECRN005A and RECRN010 is greater than the configured duration, throwing exception");
            return Mono.error(new PnPaperTrackerValidationException(
                    "The difference between RECRN005A and RECRN010 is greater than the configured duration",
                    PaperTrackingsErrorsMapper.buildPaperTrackingsError(paperTrackings, List.of(finalEvent.getStatusCode()), ErrorCategory.RENDICONTAZIONE_SCARTATA, ErrorCause.GIACENZA_DATE_ERROR,
                            String.format("RECRN005A getStatusTimestamp: %s, RECRN010 getStatusTimestamp: %s", eventRECRN00XA.getStatusTimestamp(), eventRECRN010.getStatusTimestamp()),
                            FlowThrow.FINAL_EVENT_BUILDING, ErrorType.ERROR)));
        }
        return addEventToSend(context, finalEvent, StatusCodeConfiguration.StatusCodeConfigurationEnum.fromKey(statusCode).getStatus().name());
    }

    private boolean isStockStatus(String status) {
        return RECRN003C.name().equals(status) ||
                RECRN004C.name().equals(status) ||
                RECRN005C.name().equals(status);
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

    private Mono<Void> prepareFinalEventAndPNRN012toSend(HandlerContext context, Event finalEvent, Event recrn010) {
        OffsetDateTime pnrn012Time = addDurationToInstant(recrn010.getStatusTimestamp(), pnPaperTrackerConfigs.getRefinementDuration()).atOffset(ZoneOffset.UTC);

        return buildSendEvent(context, finalEvent, StatusCodeEnum.OK, PNRN012.name(), pnrn012Time)
                .doOnNext(context.getEventsToSend()::add)
                .flatMap(se -> buildSendEvent(context, finalEvent, StatusCodeEnum.PROGRESS, finalEvent.getStatusCode(), finalEvent.getStatusTimestamp().atOffset(ZoneOffset.UTC)))
                .doOnNext(context.getEventsToSend()::add)
                .then();
    }

    private Duration getDurationBetweenDates(Instant instant1, Instant instant2) {
        return pnPaperTrackerConfigs.isEnableTruncatedDateForRefinementCheck()
                ? Duration.ofDays(ChronoUnit.DAYS.between(toRomeDate(instant1), toRomeDate(instant2)))
                : Duration.between(instant1, instant2);
    }

    private boolean isDifferenceGreaterRefinementDuration(Instant recrn010Timestamp, Instant recrn00xATimestamp) {
        log.debug("recrn010Timestamp={}, recrn00xATimestamp={}, refinementDuration={}", recrn010Timestamp, recrn00xATimestamp, pnPaperTrackerConfigs.getRefinementDuration());
        return getDurationBetweenDates(recrn010Timestamp, recrn00xATimestamp)
                .compareTo(pnPaperTrackerConfigs.getRefinementDuration()) > 0;
    }

    private boolean isDifferenceGreaterOrEqualToStockDuration(Instant recrn010Timestamp, Instant recrn005ATimestamp) {
        log.debug("recrn010Timestamp={}, recrn005ATimestamp={}, compiutaGiacenzaDuration={}", recrn010Timestamp, recrn005ATimestamp, pnPaperTrackerConfigs.getCompiutaGiacenzaArDuration());
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
                ? romeDateToInstant(toRomeDate(instant).plusDays(duration.toDays()))
                : instant.plus(duration);
    }
}