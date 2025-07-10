package it.pagopa.pn.papertracker.middleware.dao;

import it.pagopa.pn.papertracker.BaseTest;
import it.pagopa.pn.papertracker.model.ErrorCategory;
import it.pagopa.pn.papertracker.model.ErrorDetails;
import it.pagopa.pn.papertracker.model.FlowThrow;
import it.pagopa.pn.papertracker.model.ProductType;
import it.pagopa.pn.papertracker.model.PaperTrackingsErrors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.IntStream;

public class PaperTrackingsErrorsDaoIT extends BaseTest.WithLocalStack {

    @Autowired
    PaperTrackingsErrorsDAO paperTrackingsErrorsDAO;

    @Test
    void insertAndRetrieveError() {
        IntStream.range(0, 3).forEach(i -> {
            PaperTrackingsErrors error = new PaperTrackingsErrors();
            error.setRequestId("requestId1");
            error.setCreated(Instant.now().minus(i, ChronoUnit.MINUTES));
            error.setErrorCategory(ErrorCategory.UNKNOWN);
            error.setFlowThrow(FlowThrow.DEMAT_VALIDATION);
            error.setProductType(ProductType.AR);
            ErrorDetails errorDetails = new ErrorDetails();
            errorDetails.setCause("ERROR");
            errorDetails.setMessage("error");
            error.setDetails(errorDetails);
            error.setEventThrow("RECRN001C");

            paperTrackingsErrorsDAO.insertError(error).block();
        });

        PaperTrackingsErrors error = new PaperTrackingsErrors();
        error.setRequestId("requestId2");
        error.setCreated(Instant.now());
        error.setErrorCategory(ErrorCategory.UNKNOWN);
        error.setFlowThrow(FlowThrow.DEMAT_VALIDATION);
        error.setProductType(ProductType.AR);
        ErrorDetails errorDetails = new ErrorDetails();
        errorDetails.setCause("ERROR");
        errorDetails.setMessage("error");
        error.setDetails(errorDetails);
        error.setEventThrow("RECRN001C");

        paperTrackingsErrorsDAO.insertError(error).block();

        List<PaperTrackingsErrors> errors = paperTrackingsErrorsDAO.retrieveErrors("requestId1").collectList().block();

        Assertions.assertNotNull(errors);
        Assertions.assertEquals(3, errors.size());
        Assertions.assertTrue(errors.stream().allMatch(e -> e.getRequestId().equals("requestId1")));
        Assertions.assertTrue(errors.stream().allMatch(e -> e.getErrorCategory() == ErrorCategory.UNKNOWN));
        Assertions.assertTrue(errors.stream().allMatch(e -> e.getFlowThrow() == FlowThrow.DEMAT_VALIDATION));
        Assertions.assertTrue(errors.stream().allMatch(e -> e.getProductType() == ProductType.AR));
        Assertions.assertTrue(errors.stream().allMatch(e -> e.getDetails().getCause().equals("ERROR")));
        Assertions.assertTrue(errors.stream().allMatch(e -> e.getDetails().getMessage().equals("error")));
        Assertions.assertTrue(errors.stream().allMatch(e -> e.getEventThrow().equals("RECRN001C")));

        List<PaperTrackingsErrors> errors2 = paperTrackingsErrorsDAO.retrieveErrors("requestId2").collectList().block();
        Assertions.assertNotNull(errors2);
        Assertions.assertEquals(1, errors2.size());
        Assertions.assertEquals("requestId2", errors2.getFirst().getRequestId());
        Assertions.assertSame(ErrorCategory.UNKNOWN, errors2.getFirst().getErrorCategory());
        Assertions.assertSame(FlowThrow.DEMAT_VALIDATION, errors2.getFirst().getFlowThrow());
        Assertions.assertSame(errors2.getFirst().getProductType(), ProductType.AR);
        Assertions.assertEquals("ERROR", errors2.getFirst().getDetails().getCause());
        Assertions.assertEquals("error", errors2.getFirst().getDetails().getMessage());
        Assertions.assertEquals("RECRN001C", errors2.getFirst().getEventThrow());


    }
}
