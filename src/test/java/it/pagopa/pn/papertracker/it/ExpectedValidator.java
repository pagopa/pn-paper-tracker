package it.pagopa.pn.papertracker.it;

import it.pagopa.pn.papertracker.it.model.ProductTestCase;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class ExpectedValidator {

    public static void verifyErrors(ProductTestCase scenario, List<PaperTrackingsErrors> actualErrors) {
        List<PaperTrackingsErrors> expected = scenario.getExpected().getErrors();
        if (expected == null) {
            return;
        }
        assertEquals(expected.size(), actualErrors.size(), "Mismatch error count");
        for (PaperTrackingsErrors exp : expected) {
            PaperTrackingsErrors match =
                    actualErrors.stream()
                            .filter(err ->
                                    err.getTrackingId().equalsIgnoreCase(exp.getTrackingId())
                                            && err.getErrorCategory().name().equalsIgnoreCase(exp.getErrorCategory().name())
                                            && err.getFlowThrow().name().equalsIgnoreCase(exp.getFlowThrow().name())
                                            && err.getType().name().equalsIgnoreCase(exp.getType().name())
                                            && err.getProductType().equalsIgnoreCase(exp.getProductType())
                                            && (exp.getEventThrow() == null || exp.getEventThrow().equalsIgnoreCase(err.getEventThrow()))
                            )
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("Expected error not found: " + exp));

            if (exp.getDetails() != null) {
                if (exp.getDetails().getCause() != null) {
                    assertEquals(exp.getDetails().getCause(), match.getDetails().getCause());
                }
                if (exp.getDetails().getMessage() != null) {
                    assertTrue(match.getDetails().getMessage().contains(exp.getDetails().getMessage()), "Message does not contain expected text");
                }
            }
            assertNotNull(match.getCreated());
            assertNotNull(match.getTrackingId());
        }
    }

    public static void verifyOutputs(ProductTestCase scenario, List<PaperTrackerDryRunOutputs> actualOutput) {
        List<PaperTrackerDryRunOutputs> expected = scenario.getExpected().getOutputs();
        if (expected == null) {
            return;
        }
        assertEquals(expected.size(), actualOutput.size(), "Mismatch output count");
        for (PaperTrackerDryRunOutputs exp : expected) {
            PaperTrackerDryRunOutputs match =
                    actualOutput.stream()
                            .filter(out ->
                                    Objects.equals(out.getStatusCode(), exp.getStatusCode())
                                            && Objects.equals(out.getStatusDetail(), exp.getStatusDetail())
                                            && Objects.equals(out.getDeliveryFailureCause(), exp.getDeliveryFailureCause())
                                            && Objects.equals(out.getTrackingId(), exp.getTrackingId()))
                            .filter(paperTrackerDryRunOutputs -> checkAttachments(exp, paperTrackerDryRunOutputs))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("Expected output not found: " + exp));
            assertNotNull(match.getCreated());
            assertNotNull(match.getTrackingId());
        }
    }

    private static boolean checkAttachments(PaperTrackerDryRunOutputs exp, PaperTrackerDryRunOutputs out) {
        if(CollectionUtils.isEmpty(exp.getAttachments())){
            return true;
        }
        return exp.getAttachments().getFirst().getDocumentType().equalsIgnoreCase(out.getAttachments().getFirst().getDocumentType())
                && exp.getAttachments().getFirst().getId().equalsIgnoreCase(out.getAttachments().getFirst().getId());
    }

    public static void verifyTrackingEntities(ProductTestCase scenario, List<PaperTrackings> trackings) {
        List<PaperTrackings> expected = scenario.getExpected().getTrackings();
        if (expected == null) {
            return;
        }
        assertEquals(expected.size(), trackings.size(), "Mismatch Trackings count");
        for (PaperTrackings exp : expected) {
            PaperTrackings match = trackings.stream()
                    .filter(item ->
                            Objects.equals(item.getTrackingId(), exp.getTrackingId())
                                    && Objects.equals(item.getBusinessState(), exp.getBusinessState())
                                    && Objects.equals(item.getProductType(), exp.getProductType())
                                    && Objects.equals(item.getState(), exp.getState())
                                    && Objects.equals(item.getProcessingMode(), exp.getProcessingMode())
                                    && Objects.equals(item.getUnifiedDeliveryDriver(), exp.getUnifiedDeliveryDriver())
                                    && Objects.equals(item.getNextRequestIdPcretry(), exp.getNextRequestIdPcretry()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expected tracking not found: " + exp));

            ValidationConfig validationConfig = match.getValidationConfig();
            assertNotNull(validationConfig);

            ValidationFlow validationFlow = match.getValidationFlow();
           /* assertNotNull(validationFlow);
            assertNotNull(validationFlow.getSequencesValidationTimestamp());
            assertNotNull(validationFlow.getOcrRequests());
            assertEquals(0, validationFlow.getOcrRequests().size());
            assertNotNull(validationFlow.getFinalEventDematValidationTimestamp());
            assertNotNull(validationFlow.getRefinementDematValidationTimestamp());
            assertNotNull(validationFlow.getFinalEventBuilderTimestamp());
            assertNotNull(validationFlow.getRecag012StatusTimestamp());*/

            PaperStatus paperStatus = match.getPaperStatus();
      /*      Assertions.assertEquals(exp.getPaperStatus().getRegisteredLetterCode(), paperStatus.getRegisteredLetterCode());
            Assertions.assertEquals(exp.getPaperStatus().getDeliveryFailureCause(), paperStatus.getDeliveryFailureCause());
            Assertions.assertEquals(exp.getPaperStatus().getAnonymizedDiscoveredAddress(), paperStatus.getAnonymizedDiscoveredAddress());
            Assertions.assertEquals(exp.getPaperStatus().getFinalStatusCode(), paperStatus.getFinalStatusCode());*/

            assertNotNull(match.getCreatedAt());
            assertNotNull(match.getUpdatedAt());
        }
    }
}
