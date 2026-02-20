package it.pagopa.pn.papertracker.it.validator;

import it.pagopa.pn.papertracker.it.model.ProductTestCase;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Attachment;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackerDryRunOutputs;
import it.pagopa.pn.papertracker.model.OcrStatusEnum;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OutputValidator {

    public static void verifyOutputs(ProductTestCase scenario,
                                     OcrStatusEnum ocrStatusEnum,
                                     List<PaperTrackerDryRunOutputs> actualOutputs) {

        List<PaperTrackerDryRunOutputs> expected = scenario.getExpected().getOutputs();
        if (expected == null) return;

        assertEquals(expected.size(), actualOutputs.size(), "Mismatch output count");

        List<PaperTrackerDryRunOutputs> sortedActual = sortIfNeeded(scenario, ocrStatusEnum, actualOutputs);
        for (int i = 0; i < expected.size(); i++) {

            PaperTrackerDryRunOutputs exp = expected.get(i);
            PaperTrackerDryRunOutputs act = sortedActual.get(i);

            assertAll("Output at position " + i,
                    () -> assertNotNull(act.getCreated()),
                    () -> assertEquals(exp.getTrackingId(), act.getTrackingId()),
                    () -> assertEquals(exp.getRegisteredLetterCode(), act.getRegisteredLetterCode()),
                    () -> assertEquals(exp.getStatusCode(), act.getStatusCode()),
                    () -> assertEquals(exp.getStatusDetail(), act.getStatusDetail()),
                    () -> assertEquals(OffsetDateTime.parse(exp.getStatusDateTime()), OffsetDateTime.parse(act.getStatusDateTime())),
                    () -> assertEquals(exp.getDeliveryFailureCause(), act.getDeliveryFailureCause()),
                    () -> assertEquals(exp.getAnonymizedDiscoveredAddressId(), act.getAnonymizedDiscoveredAddressId()),
                    () -> assertEquals(OffsetDateTime.parse(exp.getClientRequestTimestamp()), OffsetDateTime.parse(act.getClientRequestTimestamp()))
            );

            verifyOutputAttachments(exp.getAttachments(), act.getAttachments(), i);
        }
    }

    private static List<PaperTrackerDryRunOutputs> sortIfNeeded(ProductTestCase scenario, OcrStatusEnum ocrStatusEnum, List<PaperTrackerDryRunOutputs> actualOutputs) {
        List<PaperTrackerDryRunOutputs> sorted = actualOutputs.stream()
                .sorted(Comparator.comparing(PaperTrackerDryRunOutputs::getCreated))
                .toList();
        Map<String, String> sequenceToSwap = new HashMap<>();
        sequenceToSwap.put("OK_GIACENZA_GT10_890", "RECAG005A");
        sequenceToSwap.put("OK_GIACENZA_DELEGATO_GT10_890", "RECAG006A");

        if(Optional.ofNullable(sequenceToSwap.get(scenario.getName())).isPresent() && OcrStatusEnum.RUN.equals(ocrStatusEnum)){
            List<PaperTrackerDryRunOutputs> copy = new ArrayList<>(sorted);

            int indexY = findIndexByStatus(copy, sequenceToSwap.get(scenario.getName()));
            int indexZ = findIndexByStatus(copy, "RECAG012");

            if (indexY != -1 && indexZ != -1) {
                Collections.swap(copy, indexY, indexZ);
            }
            return copy;
        }
        return sorted;
    }

    private static int findIndexByStatus(List<PaperTrackerDryRunOutputs> list, String status) {
        for (int i = 0; i < list.size(); i++) {
            if (status.equals(list.get(i).getStatusDetail())) {
                return i;
            }
        }
        return -1;
    }

    private static void verifyOutputAttachments(List<Attachment> expected,
                                                List<Attachment> actual,
                                                int parentIndex) {

        if (CollectionUtils.isEmpty(expected)) {
            assertTrue(actual == null || actual.isEmpty(),
                    "Attachments should be empty at output index " + parentIndex);
            return;
        }
        assertNotNull(actual);
        assertEquals(expected.size(), actual.size(), "Mismatch attachments count at output index " + parentIndex);
        for (int i = 0; i < expected.size(); i++) {
            Attachment expAtt = expected.get(i);
            Attachment actAtt = actual.get(i);
            assertAll("Attachment at output " + parentIndex + ", position " + i,
                    () -> assertEquals(expAtt.getId(), actAtt.getId()),
                    () -> assertEquals(expAtt.getDocumentType(), actAtt.getDocumentType()),
                    () -> assertEquals(expAtt.getUri(), actAtt.getUri()),
                    () -> assertEquals(expAtt.getDate(), actAtt.getDate())
            );
        }
    }
}
