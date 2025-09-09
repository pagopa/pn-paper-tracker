package it.pagopa.pn.papertracker.service.handler_step;

import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.AttachmentDetails;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsState.DONE;
import static org.junit.jupiter.api.Assertions.*;


public class TestUtils {

    public static PaperTrackings getPaperTrackings(String requestId) {
        PaperTrackings pt = new PaperTrackings();
        pt.setTrackingId(requestId);
        pt.setProductType(ProductType.AR);
        pt.setUnifiedDeliveryDriver("POSTE");
        pt.setState(PaperTrackingsState.AWAITING_FINAL_STATUS_CODE);
        pt.setCreatedAt(Instant.now());
        pt.setValidationFlow(new ValidationFlow());
        PaperStatus paperStatus = new PaperStatus();
        paperStatus.setEstimatedPaperDeliveryTimestamp(Instant.now());
        pt.setPaperStatus(paperStatus);
        return pt;
    }

    public static PaperTrackings getPaperTrackings(String requestId, List<Event> events) {
        PaperTrackings pt = new PaperTrackings();
        pt.setTrackingId(requestId);
        pt.setProductType(ProductType.AR);
        pt.setUnifiedDeliveryDriver("POSTE");
        pt.setState(PaperTrackingsState.AWAITING_FINAL_STATUS_CODE);
        pt.setCreatedAt(Instant.now());
        pt.setValidationFlow(new ValidationFlow());
        PaperStatus paperStatus = new PaperStatus();
        paperStatus.setEstimatedPaperDeliveryTimestamp(Instant.now());
        pt.setPaperStatus(paperStatus);
        pt.setEvents(events);
        return pt;
    }

    public static List<AttachmentDetails> constructAttachments(String statusCode, List<String> documents) {
        List<String> matches = documents.stream()
                .filter(s -> s.split("-")[0].equalsIgnoreCase(statusCode))
                .toList();
        if (!CollectionUtils.isEmpty(matches)) {
            String first = matches.getFirst();
            documents.remove(first);
            return buildAttachmentDetails(first);
        }
        return Collections.emptyList();
    }

    public static List<AttachmentDetails> buildAttachmentDetails(String token) {
        List<String> types = Arrays.asList(token.split("-")[1].split("#"));
        return types.stream().map(type -> AttachmentDetails.builder()
                .documentType(type)
                .sha256("sha256")
                .id("id-" + token)
                .uri("https://example.com/" + token)
                .date(OffsetDateTime.now())
                .build()).toList();
    }

    public static PaperProgressStatusEvent createSimpleAnalogMail(String requestId, OffsetDateTime now, AtomicInteger delay) {
        PaperProgressStatusEvent ev = new PaperProgressStatusEvent();
        ev.requestId(requestId);
        ev.setClientRequestTimeStamp(now.plusSeconds(delay.getAndIncrement()));
        ev.setStatusDateTime(now);
        ev.setIun("iun");
        ev.setProductType("AR");
        ev.setRegisteredLetterCode("registeredLetterCode");
        return ev;
    }

    public static void assertValidatedDoneSubset(PaperTrackings pt, int totalEvents, int validated, String failure, List<String> expectedValidatedCodes) {
        assertEquals(DONE, pt.getState());
        assertEquals(totalEvents, pt.getEvents().size());
        assertEquals(validated, pt.getPaperStatus().getValidatedEvents().size());
        assertTrue(pt.getPaperStatus().getValidatedEvents().stream().map(Event::getStatusCode).toList()
                .containsAll(expectedValidatedCodes));
        assertNull(pt.getNextRequestIdPcretry());
        assertEquals(failure, pt.getPaperStatus().getDeliveryFailureCause());
        assertFalse(pt.getValidationFlow().getOcrEnabled());
        assertNotNull(pt.getValidationFlow().getSequencesValidationTimestamp());
        assertNotNull(pt.getValidationFlow().getDematValidationTimestamp());
        assertNotNull(pt.getValidationFlow().getFinalEventBuilderTimestamp());
        assertNull(pt.getValidationFlow().getOcrRequestTimestamp());
        assertNotNull(pt.getPaperStatus().getRegisteredLetterCode());
        assertNotNull(pt.getPaperStatus().getFinalStatusCode());
        assertNotNull(pt.getPaperStatus().getValidatedSequenceTimestamp());
        assertNotNull(pt.getPaperStatus().getFinalDematFound());
        assertNotNull(pt.getPaperStatus().getEstimatedPaperDeliveryTimestamp());
    }

    public static void assertSingleError(List<PaperTrackingsErrors> errs, ErrorCategory cat, FlowThrow flow, String msgContains) {
        assertEquals(1, errs.size());
        PaperTrackingsErrors e = errs.getFirst();
        assertEquals(cat, e.getErrorCategory());
        assertEquals(flow, e.getFlowThrow());
        assertEquals(ErrorType.ERROR, e.getType());
        assertNotNull(e.getCreated());
        assertNotNull(e.getTrackingId());
        assertNotNull(e.getProductType());
        assertTrue(e.getDetails().getMessage().contains(msgContains));
        assertNull(e.getDetails().getCause());
    }

    public static void assertSingleWarning(List<PaperTrackingsErrors> errs, ErrorCategory cat, FlowThrow flow, String msgContains) {
        assertEquals(1, errs.size());
        PaperTrackingsErrors e = errs.getFirst();
        assertEquals(cat, e.getErrorCategory());
        assertEquals(flow, e.getFlowThrow());
        assertEquals(ErrorType.WARNING, e.getType());
        assertNotNull(e.getCreated());
        assertNotNull(e.getTrackingId());
        assertNotNull(e.getProductType());
        assertTrue(e.getDetails().getMessage().contains(msgContains));
        assertNull(e.getDetails().getCause());
    }

    public static void assertBaseDryRun(PaperTrackerDryRunOutputs e) {
        assertNotNull(e.getTrackingId());
        assertNotNull(e.getCreated());
        assertNotNull(e.getStatusDetail());
        assertNotNull(e.getStatusCode());
        assertNotNull(e.getStatusDescription());
        assertNotNull(e.getStatusDateTime());
        assertNull(e.getAnonymizedDiscoveredAddressId());
        assertNotNull(e.getClientRequestTimestamp());
    }

    public static void assertSameRegisteredLetter(List<PaperTrackerDryRunOutputs> list, int... idx) {
        String last = list.get(idx[idx.length - 1]).getRegisteredLetterCode();
        for (int i : idx) assertEquals(last, list.get(i).getRegisteredLetterCode());
    }

    public static void assertAttach(PaperTrackerDryRunOutputs e, String type) {
        assertEquals(1, e.getAttachments().size());
        assertEquals(type, e.getAttachments().getFirst().getDocumentType());
    }

    public static void assertAttachAnyOf(PaperTrackerDryRunOutputs e, String... types) {
        assertEquals(1, e.getAttachments().size());
        String t = e.getAttachments().getFirst().getDocumentType();
        assertTrue(Arrays.stream(types).anyMatch(t::equalsIgnoreCase));
    }

    public static void assertNoAttach(PaperTrackerDryRunOutputs e) {
        assertTrue(CollectionUtils.isEmpty(e.getAttachments()));
    }

    public static void assertProgress(PaperTrackerDryRunOutputs e) { assertEquals("PROGRESS", e.getStatusCode()); }
    public static void assertOk(PaperTrackerDryRunOutputs e) { assertEquals("OK", e.getStatusCode()); }
    public static void assertKo(PaperTrackerDryRunOutputs e) { assertEquals("KO", e.getStatusCode()); }

    public static boolean is(PaperTrackerDryRunOutputs e, String status) { return status.equalsIgnoreCase(e.getStatusDetail()); }
    public static boolean isOneOf(String v, String... values) { return Arrays.stream(values).anyMatch(s -> s.equalsIgnoreCase(v)); }

    public static void assertContainsStatus(List<PaperTrackerDryRunOutputs> list, List<String> expected) {
        List<String> details = list.stream().map(PaperTrackerDryRunOutputs::getStatusDetail).toList();
        assertTrue(details.containsAll(expected));
    }

    public static long count(List<PaperTrackerDryRunOutputs> list, Predicate<PaperTrackerDryRunOutputs> p) {
        return list.stream().filter(p).count();
    }

    public static void assertValidatedDone(PaperTrackings pt, int totalEvents, int validated, String failure) {
        List<Event> eventsWithoutCon = pt.getEvents().stream().filter(event -> !event.getStatusCode().startsWith("CON")).toList();
        assertEquals(DONE, pt.getState());
        assertEquals(totalEvents, pt.getEvents().size());
        assertEquals(validated, pt.getPaperStatus().getValidatedEvents().size());
        assertTrue(pt.getPaperStatus().getValidatedEvents().stream().map(Event::getStatusCode).toList()
                .containsAll(eventsWithoutCon.stream().map(Event::getStatusCode).toList()));
        assertNull(pt.getNextRequestIdPcretry());
        assertEquals(failure, pt.getPaperStatus().getDeliveryFailureCause());
        assertFalse(pt.getValidationFlow().getOcrEnabled());
        assertNotNull(pt.getValidationFlow().getSequencesValidationTimestamp());
        assertNotNull(pt.getValidationFlow().getDematValidationTimestamp());
        assertNull(pt.getValidationFlow().getOcrRequestTimestamp());
        assertNotNull(pt.getPaperStatus().getRegisteredLetterCode());
        assertNotNull(pt.getPaperStatus().getFinalStatusCode());
        assertNotNull(pt.getPaperStatus().getValidatedSequenceTimestamp());
    }
}
