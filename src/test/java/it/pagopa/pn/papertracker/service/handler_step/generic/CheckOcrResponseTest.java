package it.pagopa.pn.papertracker.service.handler_step.generic;

import com.sngular.apigenerator.asyncapi.business_model.model.event.Data;
import com.sngular.apigenerator.asyncapi.business_model.model.event.OcrDataResultPayload;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Event;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsState;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.model.HandlerContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class CheckOcrResponseTest {

    @InjectMocks
    private CheckOcrResponse checkOcrResponse;

    @Test
    void executeShouldCompleteSuccessfullyWhenValidationStatusIsOK() {
        // Arrange
        HandlerContext context = getContext(Data.ValidationStatus.OK, PaperTrackingsState.AWAITING_OCR);
        // Act & Assert
        StepVerifier.create(checkOcrResponse.execute(context))
                .verifyComplete();
    }

    @Test
    void executeShouldThrowExceptionWhenValidationStatusIsKO() {
        // Arrange
        HandlerContext context = getContext(Data.ValidationStatus.KO, PaperTrackingsState.AWAITING_OCR);
        // Act & Assert
        StepVerifier.create(checkOcrResponse.execute(context))
                .expectError(PnPaperTrackerValidationException.class)
                .verify();
    }

    @Test
    void executeShouldStopExecutionWhenValidationStatusIsPending() {
        // Arrange
        HandlerContext context = getContext(Data.ValidationStatus.PENDING, PaperTrackingsState.AWAITING_OCR);
        // Act & Assert
        StepVerifier.create(checkOcrResponse.execute(context))
                .verifyComplete();
    }

    @Test
    void executeShouldThrowExceptionWhenStateIsFinal() {
        // Arrange
        HandlerContext context = getContext(Data.ValidationStatus.OK, PaperTrackingsState.DONE);
        // Act & Assert
        StepVerifier.create(checkOcrResponse.execute(context))
                .expectError(PnPaperTrackerValidationException.class)
                .verify();
    }

    private HandlerContext getContext(Data.ValidationStatus validationStatus,
                                      PaperTrackingsState paperTrackingsState) {
        HandlerContext context = new HandlerContext();
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setOcrRequestId("ocr#event-id-1");
        paperTrackings.setState(paperTrackingsState);
        Event event = new Event();
        event.setId("event-id-1");
        event.setStatusCode("RECRN003C");
        event.setProductType(ProductType.AR);
        event.setDryRun(false);
        Event event1 = new Event();
        event1.setId("event-id-2");
        event1.setStatusCode("RECRN003B");
        event1.setProductType(ProductType.AR);
        event1.setDryRun(false);
        paperTrackings.setEvents(List.of(event, event1));
        context.setPaperTrackings(paperTrackings);
        context.setTrackingId("tracking-id-123");
        context.setOcrDataResultPayload(OcrDataResultPayload.builder()
                .CommandId("command-id-123")
                .data(Data.builder()
                        .validationStatus(validationStatus)
                        .build())
                .build());
        return context;
    }
}
