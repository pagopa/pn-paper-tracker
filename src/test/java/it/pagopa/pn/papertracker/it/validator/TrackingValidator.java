package it.pagopa.pn.papertracker.it.validator;

import it.pagopa.pn.papertracker.it.model.ProductTestCase;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.model.OcrStatusEnum;
import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsState.DONE;
import static org.junit.jupiter.api.Assertions.*;

public class TrackingValidator {

    public static void verifyTrackingEntities(ProductTestCase scenario, List<PaperTrackings> actualTrackings, OcrStatusEnum ocrStatusEnum, boolean strictFinalValidationStock890) {
        List<PaperTrackings> expectedTrackings = scenario.getExpected().getTrackings();
        if (expectedTrackings == null) return;
        assertEquals(expectedTrackings.size(), actualTrackings.size(), "Mismatch Trackings count");
        expectedTrackings.forEach(expected ->
                verifySingleTracking(scenario, expected, actualTrackings, ocrStatusEnum, strictFinalValidationStock890)
        );
    }

    private static void verifySingleTracking(ProductTestCase scenario, PaperTrackings expected, List<PaperTrackings> actualTrackings, OcrStatusEnum ocrStatusEnum, boolean strictFinalValidationStock890) {

        PaperTrackings actual = actualTrackings.stream()
                .filter(t -> t.getTrackingId().equalsIgnoreCase(expected.getTrackingId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tracking not found: " + expected.getTrackingId()));

        verifyTrackingFields(expected, actual);
        verifyEvents(expected.getEvents(), actual.getEvents());
        verifyValidationConfig(expected.getValidationConfig(), actual.getValidationConfig(),
                ocrStatusEnum, strictFinalValidationStock890);
        verifyValidationFlow(scenario, expected, actual, ocrStatusEnum);
        if(StringUtils.isBlank(expected.getNextRequestIdPcretry()) && expected.getBusinessState() == BusinessState.DONE){
            verifyPaperStatus(expected.getPaperStatus(), actual.getPaperStatus());
        }

        assertNotNull(actual.getCreatedAt());
        assertNotNull(actual.getUpdatedAt());
    }

    private static void verifyTrackingFields(PaperTrackings exp, PaperTrackings act) {
        assertAll("Tracking fields",
                () -> assertEquals(exp.getBusinessState(), act.getBusinessState()),
                () -> assertEquals(exp.getProductType(), act.getProductType()),
                () -> assertEquals(exp.getState(), act.getState()),
                () -> assertEquals(exp.getProcessingMode(), act.getProcessingMode()),
                () -> assertEquals(exp.getUnifiedDeliveryDriver(), act.getUnifiedDeliveryDriver()),
                () -> assertEquals(exp.getNextRequestIdPcretry(), act.getNextRequestIdPcretry()),
                () -> assertEquals(exp.getPcRetry(), act.getPcRetry())
        );
    }

    private static void verifyEvents(List<Event> expected, List<Event> actual) {

        assertNotNull(actual);
        assertEquals(expected.size(), actual.size(), "Mismatch events count");

        // Ordiniamo gli eventi reali per createdAt (ordine di inserimento)
        List<Event> sortedActual = actual.stream()
                .sorted(Comparator.comparing(Event::getCreatedAt))
                .toList();

        for (int i = 0; i < expected.size(); i++) {

            Event expEvent = expected.get(i);
            Event actEvent = sortedActual.get(i);

            int finalI = i;
            assertAll("Event at position " + i,
                    () -> assertNotNull(actEvent.getId()),

                    () -> assertEquals(expEvent.getRequestTimestamp(), actEvent.getRequestTimestamp(),
                            "Mismatch requestTimestamp at index " + finalI),

                    () -> assertEquals(expEvent.getStatusCode(), actEvent.getStatusCode()),
                    () -> assertEquals(expEvent.getStatusTimestamp(), actEvent.getStatusTimestamp()),
                    () -> assertEquals(expEvent.getStatusDescription(), actEvent.getStatusDescription()),
                    () -> assertEquals(expEvent.getIun(), actEvent.getIun()),
                    () -> assertEquals(expEvent.getProductType(), actEvent.getProductType()),
                    () -> assertEquals(expEvent.getDeliveryFailureCause(), actEvent.getDeliveryFailureCause()),
                    () -> assertEquals(expEvent.getRegisteredLetterCode(), actEvent.getRegisteredLetterCode()),
                    () -> assertEquals(expEvent.getNotificationReworkId(), actEvent.getNotificationReworkId()),
                    () -> assertNotNull(actEvent.getCreatedAt())
            );

            verifyAttachments(expEvent.getAttachments(), actEvent.getAttachments());
        }
    }

    private static void verifyAttachments(List<Attachment> expected, List<Attachment> actual) {
        assertEquals(expected.size(), actual.size(), "Mismatch attachments count");

        expected.forEach(expAtt -> {

            Attachment actAtt = actual.stream()
                    .filter(a -> a.getDocumentType().equalsIgnoreCase(expAtt.getDocumentType())
                            && a.getId().equalsIgnoreCase(expAtt.getId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Attachment not found: " + expAtt));

            assertAll("Attachment fields",
                    () -> assertNotNull(actAtt.getId()),
                    () -> assertEquals(expAtt.getDocumentType(), actAtt.getDocumentType())
            );
        });
    }
    private static void verifyValidationConfig(ValidationConfig expected, ValidationConfig actual, OcrStatusEnum ocrStatusEnum, boolean strictFinalValidationStock890) {

        assertNotNull(actual);

        assertAll("ValidationConfig",
                () -> assertEquals(ocrStatusEnum, actual.getOcrEnabled()),
                () -> assertEquals(expected.getRequiredAttachmentsRefinementStock890(), actual.getRequiredAttachmentsRefinementStock890()),
                () -> assertEquals(expected.getSendOcrAttachmentsFinalValidation(), actual.getSendOcrAttachmentsFinalValidation()),
                () -> assertEquals(expected.getSendOcrAttachmentsFinalValidationStock890(), actual.getSendOcrAttachmentsFinalValidationStock890()),
                () -> assertEquals(expected.getSendOcrAttachmentsRefinementStock890(), actual.getSendOcrAttachmentsRefinementStock890()),
                () -> assertEquals(strictFinalValidationStock890, actual.getStrictFinalValidationStock890())
        );
    }

    private static void verifyValidationFlow(ProductTestCase scenario, PaperTrackings expected, PaperTrackings actual, OcrStatusEnum ocrStatusEnum) {

        ValidationFlow flow = actual.getValidationFlow();
        ValidationFlow expectedFlow = expected.getValidationFlow();

        if (expected.getBusinessState() == BusinessState.DONE && StringUtils.isBlank(expected.getNextRequestIdPcretry())) {
            assertNotNull(flow.getFinalEventBuilderTimestamp());
            assertNotNull(flow.getFinalEventDematValidationTimestamp());
            assertNotNull(flow.getSequencesValidationTimestamp());
        }

        if (expected.getState() == DONE
                && scenario.getProductType().equalsIgnoreCase("890")) {
            assertNotNull(flow.getRefinementDematValidationTimestamp());
            assertNotNull(flow.getRecag012StatusTimestamp());
        }

        verifyOcrRequests(expectedFlow, flow, ocrStatusEnum, StringUtils.isNotBlank(expected.getNextRequestIdPcretry()),
                (expected.getState() == DONE || expected.getBusinessState() == BusinessState.DONE), scenario.getName());
    }

    private static void verifyOcrRequests(ValidationFlow expected, ValidationFlow actual, OcrStatusEnum ocrStatusEnum, boolean hasNextRequestIdPcretry, boolean isDone, String testCase) {

        if (ocrStatusEnum == OcrStatusEnum.DISABLED || hasNextRequestIdPcretry || (!isDone && !testCase.equalsIgnoreCase("FAIL_COMPIUTA_GIACENZA_AR"))) {
            assertTrue(actual.getOcrRequests().isEmpty());
            return;
        }

        assertFalse(actual.getOcrRequests().isEmpty());
        assertEquals(expected.getOcrRequests().size(), actual.getOcrRequests().size());

        expected.getOcrRequests().forEach(exp -> {
            OcrRequest act = actual.getOcrRequests().stream()
                    .filter(o -> o.getFinalEventId().equalsIgnoreCase(exp.getFinalEventId())
                            && o.getDocumentType().equalsIgnoreCase(exp.getDocumentType())
                            && o.getAttachmentEventId().equalsIgnoreCase(exp.getAttachmentEventId()))
                    .findFirst()
                    .orElseThrow();

            assertNotNull(act.getRequestTimestamp());
            if(ocrStatusEnum.equals(OcrStatusEnum.RUN)) {
                assertNotNull(act.getResponseTimestamp());
            }
        });
    }

    private static void verifyPaperStatus(PaperStatus exp, PaperStatus act) {

        assertAll("PaperStatus",
                () -> assertEquals(exp.getRegisteredLetterCode(), act.getRegisteredLetterCode()),
                () -> assertEquals(exp.getDeliveryFailureCause(), act.getDeliveryFailureCause()),
                () -> assertEquals(exp.getAnonymizedDiscoveredAddress(), act.getAnonymizedDiscoveredAddress()),
                () -> assertEquals(exp.getFinalStatusCode(), act.getFinalStatusCode()),
                () -> assertEquals(exp.getFinalDematFound(), act.getFinalDematFound()),
                () -> assertNotNull(act.getValidatedSequenceTimestamp()),
                () -> assertNull(act.getPaperDeliveryTimestamp()),
                () -> assertEquals(Set.copyOf(exp.getValidatedEvents()),
                        Set.copyOf(act.getValidatedEvents()))
        );
    }
}
