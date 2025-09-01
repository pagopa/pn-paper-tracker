package it.pagopa.pn.papertracker.service.handler_step.RIR;

import it.pagopa.pn.papertracker.BaseTest;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.AttachmentDetails;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PcRetryResponse;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.SingleStatusUpdate;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackerDryRunOutputsDAO;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsErrorsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.middleware.msclient.DataVaultClient;
import it.pagopa.pn.papertracker.middleware.msclient.PaperChannelClient;
import it.pagopa.pn.papertracker.middleware.msclient.SafeStorageClient;
import it.pagopa.pn.papertracker.middleware.queue.consumer.internal.ExternalChannelHandler;
import it.pagopa.pn.papertracker.service.handler_step.TestUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Predicate;

import static it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsState.DONE;
import static it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsState.KO;
import static it.pagopa.pn.papertracker.service.handler_step.RIR.TestSequenceRirEnum.*;
import static it.pagopa.pn.papertracker.service.handler_step.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class HandlerFactoryRirIT extends BaseTest.WithLocalStack {

//    @Autowired
//    private ExternalChannelHandler externalChannelHandler;
//    @Autowired
//    private SequenceConfiguration sequenceConfiguration;
//    @Autowired
//    private PaperTrackingsDAO paperTrackingsDAO;
//    @Autowired
//    private PaperTrackingsErrorsDAO paperTrackingsErrorsDAO;
//    @Autowired
//    private PaperTrackerDryRunOutputsDAO paperTrackerDryRunOutputsDAO;
//
//    @MockitoBean
//    private SafeStorageClient safeStorageClient;
//    @MockitoBean
//    private PaperChannelClient paperChannelClient;
//    @MockitoBean
//    private DataVaultClient dataVaultClient;
//
//
//    @ParameterizedTest
//    @EnumSource(value = TestSequenceRirEnum.class)
//    void testRIRSequence(TestSequenceRirEnum seq) {
//        when(safeStorageClient.getSafeStoragePresignedUrl(any())).thenReturn(Mono.just("url"));
//
//        String iun = UUID.randomUUID().toString();
//        String requestId = "PREPARE_ANALOG_DOMICILE.IUN_" + iun + ".RECINDEX_0.ATTEMPT_0.PCRETRY_0";
//        paperTrackingsDAO.putIfAbsent(getPaperTrackings(requestId)).block();
//
//        PcRetryResponse pcRetryResponse = wireRetryIfNeeded(seq.getStatusCodes(), requestId, iun);
//
//        List<String> remainingDocs = new ArrayList<>(seq.getSentDocuments());
//        OffsetDateTime now = OffsetDateTime.now();
//
//        seq.getStatusCodes().forEach(code -> {
//            PaperProgressStatusEvent ev = createSimpleAnalogMail(requestId, now);
//            var conf = StatusCodeConfiguration.StatusCodeConfigurationEnum.fromKey(code);
//            ev.setStatusCode(code);
//            ev.setStatusDescription(conf.getStatusCodeDescription());
//            if (!CollectionUtils.isEmpty(conf.getDeliveryFailureCauseList())) {
//                ev.setDeliveryFailureCause(conf.getDeliveryFailureCauseList().getFirst().name());
//            }
//            if (!CollectionUtils.isEmpty(remainingDocs)) {
//                ev.setAttachments(constructAttachments(code, remainingDocs));
//            }
//            SingleStatusUpdate msg = new SingleStatusUpdate();
//            msg.setAnalogMail(ev);
//            externalChannelHandler.handleExternalChannelMessage(msg);
//        });
//
//        PaperTrackings pt = paperTrackingsDAO.retrieveEntityByTrackingId(requestId).block();
//        PaperTrackings ptNew = (pcRetryResponse != null && StringUtils.hasText(pcRetryResponse.getRequestId()))
//                ? paperTrackingsDAO.retrieveEntityByTrackingId(pcRetryResponse.getRequestId()).block()
//                : null;
//
//        List<PaperTrackingsErrors> errors = paperTrackingsErrorsDAO.retrieveErrors(requestId).collectList().block();
//        List<PaperTrackerDryRunOutputs> outputs = paperTrackerDryRunOutputsDAO.retrieveOutputEvents(requestId).collectList().block();
//
//        assertNotNull(pt);
//        verifyPaperTrackings(pt, seq);
//        verifyNewPaperTrackings(ptNew, pt, seq);
//        verifyErrors(errors, seq);
//        assertNotNull(outputs);
//        verifyOutputs(outputs, seq);
//    }
//
//
//    private void verifyNewPaperTrackings(PaperTrackings ptNew, PaperTrackings pt, TestSequenceRirEnum seq) {
//        if (seq.equals(FURTO_SMARRIMENTO_DETERIORAMENTO)) {
//            assertNotNull(ptNew);
//            assertEquals(pt.getProductType(), ptNew.getProductType());
//            assertEquals(PaperTrackingsState.AWAITING_FINAL_STATUS_CODE, ptNew.getState());
//            assertEquals(pt.getUnifiedDeliveryDriver(), ptNew.getUnifiedDeliveryDriver());
//            assertTrue(ptNew.getTrackingId().endsWith(".RECINDEX_0.ATTEMPT_0.PCRETRY_1"));
//        } else {
//            assertNull(ptNew);
//        }
//    }
//
//    private void verifyOutputs(List<PaperTrackerDryRunOutputs> list, TestSequenceRirEnum seq) {
//        list.forEach(TestUtils::assertBaseDryRun);
//
//        switch (seq) {
//            case CONSEGNATO_FASCICOLO_CHIUSO -> {
//                assertEquals(5, list.size());
//                assertContainsStatus(list, seq.getStatusCodes());
//                assertSameRegisteredLetter(list, 0, 1, 2, 3, 4);
//                list.forEach(e -> {
//                    if (is(e, "RECRI001")) { assertNoAttach(e); assertProgress(e); }
//                    if (is(e, "RECRI002")) { assertNoAttach(e); assertProgress(e); }
//                    if (is(e, "RECRI003A")) { assertNoAttach(e); assertProgress(e); }
//                    else if (is(e, "RECRI003B")) { assertAttach(e, "AR"); assertProgress(e); }
//                    else if (is(e, "RECRI003C")) { assertNoAttach(e); assertOk(e); }
//                    assertNull(e.getDeliveryFailureCause());
//                });
//            }
//            case CONSEGNATO_FASCICOLO_CHIUSO_STATO_ASSENTE -> {
//                assertEquals(3, list.size());
//                assertContainsStatus(list, List.of("RECRI001", "RECRI002", "RECRI003B"));
//                assertSameRegisteredLetter(list, 0, 1, 2);
//                list.forEach(e -> {
//                    if (is(e, "RECRI001")) { assertNoAttach(e); assertProgress(e); }
//                    if (is(e, "RECRI002")) { assertNoAttach(e); assertProgress(e); }
//                    else if (is(e, "RECRI003B")) { assertAttach(e, "AR"); assertProgress(e); }
//                    assertNull(e.getDeliveryFailureCause());
//                });
//            }
//            case CONSEGNATO_FASCICOLO_CHIUSO_STATO_DUPLICATO -> {
//                assertEquals(7, list.size());
//                assertContainsStatus(list, seq.getStatusCodes());
//                assertSameRegisteredLetter(list, 0, 1, 2, 3, 4, 5, 6);
//                list.forEach(e -> {
//                    if (is(e, "RECRI001")) { assertNoAttach(e); assertProgress(e); }
//                    if (is(e, "RECRI002")) { assertNoAttach(e); assertProgress(e); }
//                    if (is(e, "RECRI003A")) { assertNoAttach(e); assertProgress(e); }
//                    else if (is(e, "RECRI003B")) { assertAttach(e, "AR"); assertProgress(e); }
//                    else if (is(e, "RECRI003C")) { assertNoAttach(e); assertOk(e); }
//                    assertNull(e.getDeliveryFailureCause());
//                });
//            }
//            case CONSEGNATO_FASCICOLO_CHIUSO_STATO_NON_INERENTE -> {
//                assertEquals(6, list.size());
//                assertContainsStatus(list, seq.getStatusCodes());
//                assertSameRegisteredLetter(list, 0, 1, 2, 3, 4, 5);
//                list.forEach(e -> {
//                    if (is(e, "RECRI001")) { assertNoAttach(e); assertProgress(e); }
//                    if (is(e, "RECRI002")) { assertNoAttach(e); assertProgress(e); }
//                    if (is(e, "RECRI003A")) { assertNoAttach(e); assertProgress(e); }
//                    if (is(e, "RECRI004B")) { assertNoAttach(e); assertProgress(e); }
//                    else if (is(e, "RECRI003B")) { assertAttach(e, "AR"); assertProgress(e); }
//                    else if (is(e, "RECRI003C")) { assertNoAttach(e); assertOk(e); }
//                    assertNull(e.getDeliveryFailureCause());
//                });
//            }
//            case CONSEGNATO_FASCICOLO_CHIUSO_ALLEGATO_ASSENTE -> {
//                assertEquals(4, list.size());
//                assertContainsStatus(list,List.of("RECRI001", "RECRI002", "RECRI003A", "RECRI003B"));
//                assertSameRegisteredLetter(list, 0, 1, 2, 3);
//                list.forEach(e -> {
//                    if (is(e, "RECRI001")) { assertNoAttach(e); assertProgress(e); }
//                    if (is(e, "RECRI002")) { assertNoAttach(e); assertProgress(e); }
//                    if (is(e, "RECRI003A")) { assertNoAttach(e); assertProgress(e); }
//                    else if (is(e, "RECRI003B")) { assertNoAttach(e); assertProgress(e); }
//                    assertNull(e.getDeliveryFailureCause());
//                });
//            }
//            case NON_CONSEGNATO_FASCICOLO_CHIUSO -> {
//                assertEquals(5, list.size());
//                assertContainsStatus(list, seq.getStatusCodes());
//                assertSameRegisteredLetter(list, 0, 1, 2, 3, 4);
//                list.forEach(e -> {
//                    if (is(e, "RECRI001")) { assertNoAttach(e); assertProgress(e); }
//                    if (is(e, "RECRI002")) { assertNoAttach(e); assertProgress(e); }
//                    if (is(e, "RECRI004A")) { assertNoAttach(e); assertProgress(e); }
//                    else if (is(e, "RECRI004B")) { assertAttach(e, "Plico"); assertProgress(e); }
//                    else if (is(e, "RECRI004C")) { assertNoAttach(e); assertKo(e); }
//                    assertNull(e.getDeliveryFailureCause());
//                });
//            }
//            case NON_CONSEGNATO_FASCICOLO_CHIUSO_STATO_ASSENTE -> {
//                assertEquals(3, list.size());
//                assertContainsStatus(list, List.of("RECRI001", "RECRI002", "RECRI004B"));
//                assertSameRegisteredLetter(list, 0, 1, 2);
//                list.forEach(e -> {
//                    if (is(e, "RECRI001")) { assertNoAttach(e); assertProgress(e); }
//                    if (is(e, "RECRI002")) { assertNoAttach(e); assertProgress(e); }
//                    else if (is(e, "RECRI004B")) { assertAttach(e, "Plico"); assertProgress(e); }
//                    assertNull(e.getDeliveryFailureCause());
//                });
//            }
//            case NON_CONSEGNATO_FASCICOLO_CHIUSO_STATO_DUPLICATO -> {
//                assertEquals(7, list.size());
//                assertContainsStatus(list, seq.getStatusCodes());
//                assertSameRegisteredLetter(list, 0, 1, 2, 3, 4, 5, 6);
//                list.forEach(e -> {
//                    if (is(e, "RECRI001")) { assertNoAttach(e); assertProgress(e); }
//                    if (is(e, "RECRI002")) { assertNoAttach(e); assertProgress(e); }
//                    if (is(e, "RECRI004A")) { assertNoAttach(e); assertProgress(e); }
//                    else if (is(e, "RECRI004B")) { assertAttach(e, "Plico"); assertProgress(e); }
//                    else if (is(e, "RECRI004C")) { assertNoAttach(e); assertKo(e); }
//                    assertNull(e.getDeliveryFailureCause());
//                });
//            }
//            case NON_CONSEGNATO_FASCICOLO_CHIUSO_STATO_NON_INERENTE -> {
//                assertEquals(6, list.size());
//                assertContainsStatus(list, seq.getStatusCodes());
//                assertSameRegisteredLetter(list, 0, 1, 2, 3, 4, 5);
//                list.forEach(e -> {
//                    if (is(e, "RECRI001")) { assertNoAttach(e); assertProgress(e); }
//                    if (is(e, "RECRI002")) { assertNoAttach(e); assertProgress(e); }
//                    if (is(e, "RECRI004A")) { assertNoAttach(e); assertProgress(e); }
//                    if (is(e, "RECRI004B")) { assertAttach(e, "Plico"); assertProgress(e); }
//                    if (is(e, "RECRI004C")) { assertNoAttach(e); assertKo(e); }
//                    assertNull(e.getDeliveryFailureCause());
//                });
//            }
//            case NON_CONSEGNATO_FASCICOLO_CHIUSO_ALLEGATO_ASSENTE -> {
//                assertEquals(4, list.size());
//                assertContainsStatus(list,List.of("RECRI001", "RECRI002", "RECRI004A", "RECRI004B"));
//                assertSameRegisteredLetter(list, 0, 1, 2, 3);
//                list.forEach(e -> {
//                    if (is(e, "RECRI001")) { assertNoAttach(e); assertProgress(e); }
//                    if (is(e, "RECRI002")) { assertNoAttach(e); assertProgress(e); }
//                    if (is(e, "RECRI003A")) { assertNoAttach(e); assertProgress(e); }
//                    else if (is(e, "RECRI003B")) { assertNoAttach(e); assertProgress(e); }
//                    assertNull(e.getDeliveryFailureCause());
//                });
//            }
//            case FURTO_SMARRIMENTO_DETERIORAMENTO -> {
//                assertEquals(1, list.size());
//                assertContainsStatus(list, seq.getStatusCodes());
//                list.forEach(e -> { assertNotNull(e.getRegisteredLetterCode()); assertEquals("F01", e.getDeliveryFailureCause()); assertProgress(e); });
//            }
//            case FURTO_SMARRIMENTO_DETERIORAMENTO_AVVIATO -> {
//                assertEquals(2, list.size());
//                assertContainsStatus(list, seq.getStatusCodes());
//                list.forEach(e -> {
//                    if (is(e, "RECRI001")) { assertNoAttach(e); assertProgress(e);assertNull(e.getDeliveryFailureCause());}
//                    if (is(e, "RECRI005")) { assertNoAttach(e); assertProgress(e); assertEquals("F01", e.getDeliveryFailureCause());}
//                });;
//            }
//           }
//    }
//
//     private void verifyErrors(List<PaperTrackingsErrors> errs, TestSequenceRirEnum seq) {
//        switch (seq) {
//            case CONSEGNATO_FASCICOLO_CHIUSO,  CONSEGNATO_FASCICOLO_CHIUSO_STATO_DUPLICATO,
//                 CONSEGNATO_FASCICOLO_CHIUSO_STATO_NON_INERENTE, NON_CONSEGNATO_FASCICOLO_CHIUSO, NON_CONSEGNATO_FASCICOLO_CHIUSO_STATO_DUPLICATO,
//                 NON_CONSEGNATO_FASCICOLO_CHIUSO_STATO_NON_INERENTE, FURTO_SMARRIMENTO_DETERIORAMENTO -> assertEquals(0, errs.size());
//
//            case FURTO_SMARRIMENTO_DETERIORAMENTO_AVVIATO ->
//                    assertSingleError(errs, ErrorCategory.MAX_RETRY_REACHED_ERROR, FlowThrow.RETRY_PHASE,
//                            "Retry not found for trackingId: ");
//
//            case CONSEGNATO_FASCICOLO_CHIUSO_ALLEGATO_ASSENTE,
//                 NON_CONSEGNATO_FASCICOLO_CHIUSO_ALLEGATO_ASSENTE ->
//                    assertSingleError(errs, ErrorCategory.ATTACHMENTS_ERROR, FlowThrow.SEQUENCE_VALIDATION,
//                            "Attachments are not valid for the sequence element: ");
//
//            case CONSEGNATO_FASCICOLO_CHIUSO_STATO_ASSENTE, NON_CONSEGNATO_FASCICOLO_CHIUSO_STATO_ASSENTE ->
//                    assertSingleError(errs, ErrorCategory.STATUS_CODE_ERROR, FlowThrow.SEQUENCE_VALIDATION,
//                            "Necessary status code not found in events");
//        }
//    }
//
//    private void verifyPaperTrackings(PaperTrackings pt, TestSequenceRirEnum seq) {
//        assertNull(pt.getOcrRequestId());
//        assertTrue(pt.getEvents().stream().map(Event::getStatusCode).toList().containsAll(seq.getStatusCodes()));
//
//        switch (seq) {
//            case CONSEGNATO_FASCICOLO_CHIUSO, NON_CONSEGNATO_FASCICOLO_CHIUSO -> assertValidatedDone(pt, 5, 5, null);
//            case CONSEGNATO_FASCICOLO_CHIUSO_STATO_DUPLICATO ->
//                    assertValidatedDoneSubset(pt, 7, 5, null, List.of("RECRI001", "RECRI002", "RECRI003A", "RECRI003B", "RECRI003C"));
//            case NON_CONSEGNATO_FASCICOLO_CHIUSO_STATO_DUPLICATO ->
//                    assertValidatedDoneSubset(pt, 7, 5, null, List.of("RECRI001", "RECRI002", "RECRI004A", "RECRI004B", "RECRI004C"));
//            case NON_CONSEGNATO_FASCICOLO_CHIUSO_STATO_NON_INERENTE ->
//                    assertValidatedDoneSubset(pt, 6, 5, null, List.of("RECRI001", "RECRI002", "RECRI004A", "RECRI004B", "RECRI004C"));
//            case CONSEGNATO_FASCICOLO_CHIUSO_STATO_NON_INERENTE ->
//                    assertValidatedDoneSubset(pt, 6, 5, null, List.of("RECRI001", "RECRI002", "RECRI003A", "RECRI003B", "RECRI003C"));
//
//            case FURTO_SMARRIMENTO_DETERIORAMENTO -> {
//                assertEquals(DONE, pt.getState());
//                assertEquals(1, pt.getEvents().size());
//                assertNotNull(pt.getNextRequestIdPcretry());
//            }
//            case FURTO_SMARRIMENTO_DETERIORAMENTO_AVVIATO -> {
//                assertEquals(DONE, pt.getState());
//                assertEquals(2, pt.getEvents().size());
//                assertNull(pt.getNextRequestIdPcretry());
//            }
//
//            case CONSEGNATO_FASCICOLO_CHIUSO_ALLEGATO_ASSENTE,
//                 NON_CONSEGNATO_FASCICOLO_CHIUSO_ALLEGATO_ASSENTE -> {
//                assertEquals(KO, pt.getState());
//                assertEquals(5, pt.getEvents().size());
//                assertNull(pt.getNextRequestIdPcretry());
//            }
//
//            case NON_CONSEGNATO_FASCICOLO_CHIUSO_STATO_ASSENTE,
//                 CONSEGNATO_FASCICOLO_CHIUSO_STATO_ASSENTE -> {
//                assertEquals(KO, pt.getState());
//                assertEquals(4, pt.getEvents().size());
//                assertNull(pt.getNextRequestIdPcretry());
//            }
//        }
//    }
//
//    private PcRetryResponse wireRetryIfNeeded(List<String> statusCodes, String requestId, String iun) {
//        if (!statusCodes.contains("RECRI005")) return null;
//
//        PcRetryResponse resp = new PcRetryResponse();
//        if (statusCodes.size() == 1) {
//            String newRequestId = "PREPARE_ANALOG_DOMICILE.IUN_" + iun + ".RECINDEX_0.ATTEMPT_0.PCRETRY_1";
//            resp.setRetryFound(true);
//            resp.setRequestId(newRequestId);
//            resp.setParentRequestId(requestId);
//            resp.setDeliveryDriverId("POSTE");
//            resp.setPcRetry("PCRETRY_1");
//        } else {
//            resp.setRetryFound(false);
//        }
//        when(paperChannelClient.getPcRetry(any())).thenReturn(Mono.just(resp));
//        return resp;
//    }
}
