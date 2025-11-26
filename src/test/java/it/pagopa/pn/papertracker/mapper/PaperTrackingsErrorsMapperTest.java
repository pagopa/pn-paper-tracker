package it.pagopa.pn.papertracker.mapper;

import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingError;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;

public class PaperTrackingsErrorsMapperTest {

    @Test
    void shouldSetAndGetAllFieldsCorrectly() {
        PaperTrackingsErrors paperTrackingsErrors = new PaperTrackingsErrors();
        paperTrackingsErrors.setTrackingId("trackingId");
        paperTrackingsErrors.setErrorCategory(ErrorCategory.OCR_VALIDATION);
        paperTrackingsErrors.setCreated(Instant.now());
        paperTrackingsErrors.setDetails(ErrorDetails.builder().cause(ErrorCause.OCR_KO).message("errorMessage").build());
        paperTrackingsErrors.setFlowThrow(FlowThrow.DEMAT_VALIDATION);
        paperTrackingsErrors.setEventThrow("eventThrow");
        paperTrackingsErrors.setEventIdThrow("eventIdThrow");
        paperTrackingsErrors.setProductType(ProductType._890.getValue());
        paperTrackingsErrors.setType(ErrorType.ERROR);

        TrackingError trackingError = PaperTrackingsErrorsMapper.toTrackingError(paperTrackingsErrors);

        Assertions.assertEquals("trackingId", trackingError.getTrackingId());
        Assertions.assertEquals(ErrorCategory.OCR_VALIDATION.name(), trackingError.getErrorCategory());
        Assertions.assertNotNull(trackingError.getCreated());
        Assertions.assertNotNull(trackingError.getDetails());
        Assertions.assertEquals(ErrorCause.OCR_KO.name(), trackingError.getDetails().getCause());
        Assertions.assertEquals("errorMessage", trackingError.getDetails().getMessage());
        Assertions.assertNotNull(trackingError.getFlowThrow());
        Assertions.assertEquals(FlowThrow.DEMAT_VALIDATION.name(), trackingError.getFlowThrow().name());
        Assertions.assertEquals("eventThrow", trackingError.getEventThrow());
        Assertions.assertEquals("eventIdThrow", trackingError.getEventIdThrow());
        Assertions.assertNotNull(trackingError.getProductType());
        Assertions.assertEquals(ProductType._890.getValue(), trackingError.getProductType());
        Assertions.assertNotNull(trackingError.getType());
        Assertions.assertEquals(ErrorType.ERROR.name(), trackingError.getType().name());

    }
}
