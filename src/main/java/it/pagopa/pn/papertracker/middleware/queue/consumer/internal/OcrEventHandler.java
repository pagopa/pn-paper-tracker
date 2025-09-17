package it.pagopa.pn.papertracker.middleware.queue.consumer.internal;

import com.sngular.apigenerator.asyncapi.business_model.model.event.Data;
import com.sngular.apigenerator.asyncapi.business_model.model.event.OcrDataResultPayload;
import it.pagopa.pn.commons.utils.MDCUtils;
import it.pagopa.pn.papertracker.exception.PaperTrackerException;
import it.pagopa.pn.papertracker.exception.PaperTrackerExceptionHandler;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.mapper.PaperTrackingsErrorsMapper;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.AR.HandlersFactoryAr;
import it.pagopa.pn.papertracker.service.handler_step.RIR.HandlersFactoryRir;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Component
@RequiredArgsConstructor
@CustomLog
public class OcrEventHandler {

    private final PaperTrackingsDAO paperTrackingsDAO;
    private final HandlersFactoryAr handlersFactoryAr;
    private final HandlersFactoryRir handlersFactoryRir;
    private final PaperTrackerExceptionHandler paperTrackerExceptionHandler;

    public void handleOcrMessage(OcrDataResultPayload ocrResultMessage) {
        if (Objects.isNull(ocrResultMessage) || Objects.isNull(ocrResultMessage.getCommandId())) {
            log.error("Received null payload or commandId in OcrResponse handler");
            throw new IllegalArgumentException("Payload or commandId cannot be null");
        }

        String processName = "processOcrResponseMessage";
        MDC.put(MDCUtils.MDC_PN_CTX_REQUEST_ID, ocrResultMessage.getCommandId());
        log.logStartingProcess(processName);

        MDCUtils.addMDCToContextAndExecute(paperTrackingsDAO.retrieveEntityByOcrRequestId(ocrResultMessage.getCommandId())
                        .flatMap(paperTrackings -> {
                            if(paperTrackings.getState().equals(PaperTrackingsState.DONE) || paperTrackings.getState().equals(PaperTrackingsState.KO)){
                                String eventId = paperTrackings.getOcrRequestId().split("#")[1];
                                Mono.error(new PnPaperTrackerValidationException(("Error in OCR validation for requestId: " + ocrResultMessage.getCommandId()),
                                        PaperTrackingsErrorsMapper.buildPaperTrackingsError(paperTrackings,
                                                "",
                                                ErrorCategory.OCR_VALIDATION,
                                                ErrorCause.OCR_DUPLICATED_EVENT,
                                                "CommandId: " + ocrResultMessage.getCommandId(),
                                                FlowThrow.DEMAT_VALIDATION,
                                                ErrorType.WARNING,
                                                eventId
                                        )));
                            }
                            Event event = extractFinalEventFromOcr(paperTrackings);
                            String statusCode = event.getStatusCode();
                            Data.ValidationStatus validationStatus = ocrResultMessage.getData().getValidationStatus();
                            return switch (validationStatus) {
                                case KO -> Mono.error(new PnPaperTrackerValidationException(("Error in OCR validation for requestId: " + ocrResultMessage.getCommandId()),
                                                PaperTrackingsErrorsMapper.buildPaperTrackingsError(paperTrackings,
                                                        statusCode,
                                                        ErrorCategory.OCR_VALIDATION,
                                                        ErrorCause.OCR_KO,
                                                        ocrResultMessage.getData().getDescription(),
                                                        FlowThrow.DEMAT_VALIDATION,
                                                        ErrorType.ERROR,
                                                        event.getId()
                                                        )));
                                case OK -> callOcrResponseHandler(paperTrackings, event);
                                case PENDING -> {
                                    log.info("Ocr validation is still pending for requestId: {}", ocrResultMessage.getCommandId());
                                    yield Mono.empty();
                                }
                            };
                        })
                        .onErrorResume(PnPaperTrackerValidationException.class, e -> paperTrackerExceptionHandler.handleInternalException(e, null))
                        .then())
                .block();

    }

    private Mono<Void> callOcrResponseHandler(PaperTrackings paperTrackings, Event event) {
        HandlerContext context = buildContextAndAddDematValidationTimestamp(paperTrackings, event);
        return switch (paperTrackings.getProductType()){
            case AR -> handlersFactoryAr.buildOcrResponseHandler(context);
            case RIR -> handlersFactoryRir.buildOcrResponseHandler(context);
            default -> Mono.error(new PaperTrackerException("Invalid productType: " + paperTrackings.getProductType()));
        };
    }

    private Event extractFinalEventFromOcr(PaperTrackings paperTrackings) {
        String eventId = paperTrackings.getOcrRequestId().split("#")[1];
        return paperTrackings.getEvents().stream()
                .filter(event -> eventId.equalsIgnoreCase(event.getId()))
                .findFirst()
                .orElseThrow(() -> new PaperTrackerException("Invalid ocr requestId: " + paperTrackings.getOcrRequestId() +
                        ". The event with id " + eventId + " does not exist in the paperTrackings events list."));
    }

    private HandlerContext buildContextAndAddDematValidationTimestamp(PaperTrackings paperTrackings, Event event) {
        HandlerContext handlerContext = new HandlerContext();
        handlerContext.setTrackingId(paperTrackings.getTrackingId());
        handlerContext.setPaperTrackings(paperTrackings);
        handlerContext.setEventId(event.getId());
        handlerContext.setDryRunEnabled(event.getDryRun());
        return handlerContext;
    }
}
