package it.pagopa.pn.papertracker.it.validator;

import it.pagopa.pn.papertracker.it.model.ProductTestCase;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Attachment;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackerDryRunOutputs;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OutputValidator {

    public static void verifyOutputs(ProductTestCase scenario,
                                     List<PaperTrackerDryRunOutputs> actualOutputs) {

        List<PaperTrackerDryRunOutputs> expected = scenario.getExpected().getOutputs();
        if (expected == null) return;

        assertEquals(expected.size(), actualOutputs.size(), "Mismatch output count");

        List<PaperTrackerDryRunOutputs> sortedActual = actualOutputs.stream()
                .sorted(Comparator.comparing(PaperTrackerDryRunOutputs::getCreated))
                .toList();

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
