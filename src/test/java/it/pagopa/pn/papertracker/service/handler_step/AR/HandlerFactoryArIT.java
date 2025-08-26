package it.pagopa.pn.papertracker.service.handler_step.AR;

import it.pagopa.pn.papertracker.BaseTest;
import it.pagopa.pn.papertracker.config.SequenceConfiguration;
import it.pagopa.pn.papertracker.config.StatusCodeConfiguration;
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
import static it.pagopa.pn.papertracker.service.handler_step.AR.TestSequenceEnum.FURTO_SMARRIMENTO_DETERIORAMENTO;
import static it.pagopa.pn.papertracker.service.handler_step.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class HandlerFactoryArIT extends BaseTest.WithLocalStack {

    @Autowired
    private ExternalChannelHandler externalChannelHandler;
    @Autowired
    private SequenceConfiguration sequenceConfiguration;
    @Autowired
    private PaperTrackingsDAO paperTrackingsDAO;
    @Autowired
    private PaperTrackingsErrorsDAO paperTrackingsErrorsDAO;
    @Autowired
    private PaperTrackerDryRunOutputsDAO paperTrackerDryRunOutputsDAO;

    @MockitoBean
    private SafeStorageClient safeStorageClient;
    @MockitoBean
    private PaperChannelClient paperChannelClient;
    @MockitoBean
    private DataVaultClient dataVaultClient;


    @ParameterizedTest
    @EnumSource(value = TestSequenceEnum.class)
    void testARSequence(TestSequenceEnum seq) {
        when(safeStorageClient.getSafeStoragePresignedUrl(any())).thenReturn(Mono.just("url"));

        String iun = UUID.randomUUID().toString();
        String requestId = "PREPARE_ANALOG_DOMICILE.IUN_" + iun + ".RECINDEX_0.ATTEMPT_0.PCRETRY_0";
        paperTrackingsDAO.putIfAbsent(getPaperTrackings(requestId)).block();

        PcRetryResponse pcRetryResponse = wireRetryIfNeeded(seq.getStatusCodes(), requestId, iun);

        List<String> remainingDocs = new ArrayList<>(seq.getSentDocuments());
        OffsetDateTime now = OffsetDateTime.now();

        seq.getStatusCodes().forEach(code -> {
            PaperProgressStatusEvent ev = createSimpleAnalogMail(requestId, now);
            if (isOneOf(code, "RECRN005C","RECRN005B","RECRN005A")) {
                ev.setClientRequestTimeStamp(ev.getClientRequestTimeStamp().plusDays(60));
                ev.setStatusDateTime(ev.getStatusDateTime().plusDays(60));
            }
            var conf = StatusCodeConfiguration.StatusCodeConfigurationEnum.fromKey(code);
            ev.setStatusCode(code);
            ev.setStatusDescription(conf.getStatusCodeDescription());
            if (!CollectionUtils.isEmpty(conf.getDeliveryFailureCauseList())) {
                ev.setDeliveryFailureCause(conf.getDeliveryFailureCauseList().getFirst().name());
            }
            if (!CollectionUtils.isEmpty(remainingDocs)) {
                ev.setAttachments(constructAttachments(code, remainingDocs));
            }
            SingleStatusUpdate msg = new SingleStatusUpdate();
            msg.setAnalogMail(ev);
            externalChannelHandler.handleExternalChannelMessage(msg);
        });

        PaperTrackings pt = paperTrackingsDAO.retrieveEntityByTrackingId(requestId).block();
        PaperTrackings ptNew = (pcRetryResponse != null && StringUtils.hasText(pcRetryResponse.getRequestId()))
                ? paperTrackingsDAO.retrieveEntityByTrackingId(pcRetryResponse.getRequestId()).block()
                : null;

        List<PaperTrackingsErrors> errors = paperTrackingsErrorsDAO.retrieveErrors(requestId).collectList().block();
        List<PaperTrackerDryRunOutputs> outputs = paperTrackerDryRunOutputsDAO.retrieveOutputEvents(requestId).collectList().block();

        verifyPaperTrackings(pt, seq);
        verifyNewPaperTrackings(ptNew, pt, seq);
        verifyErrors(errors, seq);
        verifyOutputs(outputs, seq);
    }


    private void verifyNewPaperTrackings(PaperTrackings ptNew, PaperTrackings pt, TestSequenceEnum seq) {
        if (seq.equals(FURTO_SMARRIMENTO_DETERIORAMENTO)) {
            assertNotNull(ptNew);
            assertEquals(pt.getProductType(), ptNew.getProductType());
            assertEquals(PaperTrackingsState.AWAITING_FINAL_STATUS_CODE, ptNew.getState());
            assertEquals(pt.getUnifiedDeliveryDriver(), ptNew.getUnifiedDeliveryDriver());
            assertTrue(ptNew.getTrackingId().endsWith(".RECINDEX_0.ATTEMPT_0.PCRETRY_1"));
        } else {
            assertNull(ptNew);
        }
    }

    private void verifyOutputs(List<PaperTrackerDryRunOutputs> list, TestSequenceEnum seq) {
        list.forEach(TestUtils::assertBaseDryRun);

        switch (seq) {
            case CONSEGNATO_FASCICOLO_CHIUSO -> {
                assertEquals(3, list.size());
                assertContainsStatus(list, seq.getStatusCodes());
                assertSameRegisteredLetter(list, 0, 1, 2);
                list.forEach(e -> {
                    if (is(e, "RECRN001A")) { assertNoAttach(e); assertProgress(e); }
                    else if (is(e, "RECRN001B")) { assertAttach(e, "AR"); assertProgress(e); }
                    else if (is(e, "RECRN001C")) { assertNoAttach(e); assertOk(e); }
                    assertNull(e.getDeliveryFailureCause());
                });
            }
            case MANCATA_CONSEGNA_FASCICOLO_CHIUSO -> {
                assertEquals(3, list.size());
                assertContainsStatus(list, seq.getStatusCodes());
                assertSameRegisteredLetter(list, 0, 1, 2);
                list.forEach(e -> {
                    if (is(e, "RECRN002A")) { assertNoAttach(e); assertNull(e.getDeliveryFailureCause()); assertProgress(e); }
                    else if (is(e, "RECRN002B")) { assertAttach(e, "Plico"); assertNotNull(e.getDeliveryFailureCause()); assertProgress(e); }
                    else if (is(e, "RECRN002C")) { assertNoAttach(e); assertNull(e.getDeliveryFailureCause()); assertOk(e); }
                });
            }
            case IRREPERIBILITA_ASSOLUTA_FASCICOLO_CHIUSO -> {
                assertEquals(4, list.size());
                assertContainsStatus(list, seq.getStatusCodes());
                assertEquals(2, count(list, e -> is(e, "RECRN002E")));
                assertSameRegisteredLetter(list, 0, 1, 2, 3);
                list.forEach(e -> {
                    if (is(e, "RECRN002D")) { assertNoAttach(e); assertNull(e.getDeliveryFailureCause()); assertProgress(e); }
                    else if (is(e, "RECRN002E")) { assertAttachAnyOf(e, "Plico","Indagine"); assertNotNull(e.getDeliveryFailureCause()); assertProgress(e); }
                    else if (is(e, "RECRN002F")) { assertNoAttach(e); assertNull(e.getDeliveryFailureCause()); assertKo(e); }
                });
            }
            case FURTO_SMARRIMENTO_DETERIORAMENTO -> {
                assertEquals(1, list.size());
                assertContainsStatus(list, seq.getStatusCodes());
                list.forEach(e -> { assertNotNull(e.getRegisteredLetterCode()); assertNull(e.getDeliveryFailureCause()); assertProgress(e); });
            }
            case INESITO_FURTO_SMARRIMENTO_DETERIORAMENTO -> {
                assertEquals(2, list.size());
                assertContainsStatus(list, seq.getStatusCodes());
                list.forEach(e -> { assertNotNull(e.getRegisteredLetterCode()); assertNull(e.getDeliveryFailureCause()); assertProgress(e); });
            }
            case INESITO_INGIACENZA_FURTO_SMARRIMENTO_DETERIORAMENTO -> {
                assertEquals(3, list.size());
                assertContainsStatus(list, seq.getStatusCodes());
                list.forEach(e -> { assertNotNull(e.getRegisteredLetterCode()); assertNull(e.getDeliveryFailureCause()); assertProgress(e); });
            }
            case CONSEGNATO_PRESSO_GIACENZA_FASCICOLO_CHIUSO_ALLEGATI_MANCANTI -> {
                assertEquals(4, list.size());
                assertContainsStatus(list, List.of("RECRN010","RECRN011","RECRN003A","RECRN003B"));
                list.forEach(e -> { assertNoAttach(e); assertNull(e.getDeliveryFailureCause()); assertProgress(e); });
            }
            case CONSEGNATO_PRESSO_GIACENZA_FASCICOLO_CHIUSO -> {
                assertEquals(5, list.size());
                assertContainsStatus(list, seq.getStatusCodes());
                list.forEach(e -> {
                    if (is(e, "RECRN003A")) { assertNoAttach(e); assertNull(e.getDeliveryFailureCause()); assertProgress(e); }
                    else if (is(e, "RECRN003B")) { assertAttach(e, "AR"); assertNull(e.getDeliveryFailureCause()); assertProgress(e); }
                    else if (is(e, "RECRN003C")) { assertNoAttach(e); assertNull(e.getDeliveryFailureCause()); assertOk(e); }
                });
            }
            case MANCATA_CONSEGNA_PRESSO_GIACENZA_FASCICOLO_CHIUSO -> {
                assertEquals(5, list.size());
                assertContainsStatus(list, seq.getStatusCodes());
                list.forEach(e -> {
                    if (is(e, "RECRN004A")) { assertNoAttach(e); assertNull(e.getDeliveryFailureCause()); assertProgress(e); }
                    else if (is(e, "RECRN004B")) { assertAttach(e, "Plico"); assertNull(e.getDeliveryFailureCause()); assertProgress(e); }
                    else if (is(e, "RECRN004C")) { assertNoAttach(e); assertNull(e.getDeliveryFailureCause()); assertOk(e); }
                });
            }
            case COMPIUTA_GIACENZA_PRESSO_GIACENZA_FASCICOLO_CHIUSO -> {
                assertContainsStatus(list, seq.getStatusCodes());
                assertTrue(list.stream().anyMatch(e -> is(e,"PNRN012")));
                list.forEach(e -> {
                    if (is(e, "RECRN005A")) { assertNoAttach(e); assertNull(e.getDeliveryFailureCause()); assertProgress(e); }
                    else if (is(e, "RECRN005B")) { assertAttach(e, "Plico"); assertNull(e.getDeliveryFailureCause()); assertProgress(e); }
                    else if (is(e, "RECRN005C")) { assertNoAttach(e); assertNull(e.getDeliveryFailureCause()); assertProgress(e); }
                    else if (is(e, "PNRN012")) { assertNoAttach(e); assertNull(e.getDeliveryFailureCause()); assertOk(e); }
                });
            }
            case CONSEGNATO_FASCICOLO_CHIUSO_AB_DUPLICATI_OK -> {
                assertEquals(6, list.size());
                assertContainsStatus(list, seq.getStatusCodes());
                list.forEach(e -> {
                    if (is(e, "RECRN001A")) { assertNoAttach(e); assertNull(e.getDeliveryFailureCause()); assertProgress(e); }
                    else if (is(e, "RECRN001B")) { assertAttach(e, "AR"); assertNull(e.getDeliveryFailureCause()); assertProgress(e); }
                    else if (is(e, "RECRN001C")) { assertNoAttach(e); assertNull(e.getDeliveryFailureCause()); assertOk(e); }
                });
            }
            case CONSEGNATO_PRESSO_GIACENZA_FASCICOLO_CHIUSO_010_DUPLICATO_OK,
                 CONSEGNATO_PRESSO_GIACENZA_FASCICOLO_CHIUSO_011_DUPLICATO_OK -> {
                assertEquals(6, list.size());
                assertContainsStatus(list, seq.getStatusCodes());
                list.forEach(e -> {
                    if (is(e, "RECRN003A")) { assertNoAttach(e); assertNull(e.getDeliveryFailureCause()); assertProgress(e); }
                    else if (is(e, "RECRN003B")) { assertAttach(e, "AR"); assertNull(e.getDeliveryFailureCause()); assertProgress(e); }
                    else if (is(e, "RECRN003C")) { assertNoAttach(e); assertNull(e.getDeliveryFailureCause()); assertOk(e); }
                });
            }
            case CONSEGNATO_PRESSO_GIACENZA_FASCICOLO_CHIUSO_STATO_ASSENTE -> {
                assertEquals(4, list.size());
                assertContainsStatus(list, List.of("RECRN010","RECRN010","RECRN003A","RECRN003B"));
                list.forEach(e -> {
                    if (is(e, "RECRN003A")) { assertNoAttach(e); assertNull(e.getDeliveryFailureCause()); assertProgress(e); }
                    else if (is(e, "RECRN003B")) { assertAttach(e, "AR"); assertNull(e.getDeliveryFailureCause()); assertProgress(e); }
                });
            }
            case CONSEGNATO_FASCICOLO_CHIUSO_STATO_NON_INERENTE -> {
                assertEquals(4, list.size());
                assertContainsStatus(list, seq.getStatusCodes());
                list.forEach(e -> {
                    assertNotNull(e.getRegisteredLetterCode());
                    assertNull(e.getDeliveryFailureCause());
                    if (is(e, "RECRN001A")) { assertNoAttach(e); assertProgress(e); }
                    else if (is(e, "RECRN001B")) { assertAttach(e, "AR"); assertProgress(e); }
                    else if (is(e, "RECRN001C")) { assertNoAttach(e); assertOk(e); }
                });
            }
            case MANCATA_CONSEGNA_PRESSO_GIACENZA_FASCICOLO_CHIUSO_STATO_NON_INERENTE -> {
                assertEquals(7, list.size());
                assertContainsStatus(list, seq.getStatusCodes());
                list.forEach(e -> {
                    assertNotNull(e.getRegisteredLetterCode());
                    assertNull(e.getDeliveryFailureCause());
                    if (is(e, "RECRN001A")) { assertNoAttach(e); assertProgress(e); }
                    else if (is(e, "RECRN003B")) { assertAttach(e, "AR"); assertProgress(e); }
                    else if (is(e, "RECRN004B")) { assertAttach(e, "Plico"); assertProgress(e); }
                    else if (is(e, "RECRN001C")) { assertNoAttach(e); assertOk(e); }
                });
            }
            case IRREPERIBILITA_ASSOLUTA_FASCICOLO_CHIUSO_ALLEGATI_SEPARATI -> {
                assertEquals(4, list.size());
                assertContainsStatus(list, seq.getStatusCodes());
                list.forEach(e -> {
                    if (is(e, "RECRN002D")) { assertNoAttach(e); assertNull(e.getDeliveryFailureCause()); assertProgress(e); }
                    else if (is(e, "RECRN002E")) { assertAttachAnyOf(e, "Plico","Indagine"); assertNotNull(e.getDeliveryFailureCause()); assertProgress(e); }
                    else if (is(e, "RECRN002F")) { assertNoAttach(e); assertNull(e.getDeliveryFailureCause()); assertKo(e); }
                });
            }
            case IRREPERIBILITA_ASSOLUTA_FASCICOLO_CHIUSO_ALLEGATI_MANCANTI -> {
                assertEquals(2, list.size());
                assertContainsStatus(list, List.of("RECRN002D","RECRN002E"));
                list.forEach(e -> {
                    if (is(e, "RECRN002D")) { assertNoAttach(e); assertNull(e.getDeliveryFailureCause()); assertProgress(e); }
                    else if (is(e, "RECRN002E")) { assertAttach(e, "Plico"); assertNotNull(e.getDeliveryFailureCause()); assertProgress(e); }
                });
            }
        }
    }

    private void verifyErrors(List<PaperTrackingsErrors> errs, TestSequenceEnum seq) {
        switch (seq) {
            case CONSEGNATO_FASCICOLO_CHIUSO, MANCATA_CONSEGNA_FASCICOLO_CHIUSO,
                 IRREPERIBILITA_ASSOLUTA_FASCICOLO_CHIUSO, FURTO_SMARRIMENTO_DETERIORAMENTO,
                 CONSEGNATO_PRESSO_GIACENZA_FASCICOLO_CHIUSO, MANCATA_CONSEGNA_PRESSO_GIACENZA_FASCICOLO_CHIUSO,
                 CONSEGNATO_FASCICOLO_CHIUSO_AB_DUPLICATI_OK,
                 CONSEGNATO_PRESSO_GIACENZA_FASCICOLO_CHIUSO_010_DUPLICATO_OK,
                 CONSEGNATO_PRESSO_GIACENZA_FASCICOLO_CHIUSO_011_DUPLICATO_OK,
                 CONSEGNATO_FASCICOLO_CHIUSO_STATO_NON_INERENTE,
                 MANCATA_CONSEGNA_PRESSO_GIACENZA_FASCICOLO_CHIUSO_STATO_NON_INERENTE,
                 IRREPERIBILITA_ASSOLUTA_FASCICOLO_CHIUSO_ALLEGATI_SEPARATI,
                 COMPIUTA_GIACENZA_PRESSO_GIACENZA_FASCICOLO_CHIUSO -> assertEquals(0, errs.size());

            case INESITO_FURTO_SMARRIMENTO_DETERIORAMENTO, INESITO_INGIACENZA_FURTO_SMARRIMENTO_DETERIORAMENTO ->
                    assertSingleError(errs, ErrorCategory.MAX_RETRY_REACHED_ERROR, FlowThrow.RETRY_PHASE,
                            "Retry not found for trackingId: ");

            case CONSEGNATO_PRESSO_GIACENZA_FASCICOLO_CHIUSO_ALLEGATI_MANCANTI,
                 IRREPERIBILITA_ASSOLUTA_FASCICOLO_CHIUSO_ALLEGATI_MANCANTI ->
                    assertSingleError(errs, ErrorCategory.ATTACHMENTS_ERROR, FlowThrow.SEQUENCE_VALIDATION,
                            "Attachments are not valid for the sequence element: ");

            case CONSEGNATO_PRESSO_GIACENZA_FASCICOLO_CHIUSO_STATO_ASSENTE ->
                    assertSingleError(errs, ErrorCategory.STATUS_CODE_ERROR, FlowThrow.SEQUENCE_VALIDATION,
                            "Necessary status code not found in events");
        }
    }

    private void verifyPaperTrackings(PaperTrackings pt, TestSequenceEnum seq) {
        assertNull(pt.getOcrRequestId());
        assertTrue(pt.getEvents().stream().map(Event::getStatusCode).toList().containsAll(seq.getStatusCodes()));

        switch (seq) {
            case CONSEGNATO_FASCICOLO_CHIUSO -> assertValidatedDone(pt, 3, 3, null);
            case MANCATA_CONSEGNA_FASCICOLO_CHIUSO -> assertValidatedDone(pt, 3, 3, "M02");
            case IRREPERIBILITA_ASSOLUTA_FASCICOLO_CHIUSO -> assertValidatedDone(pt, 3, 3, "M01");

            case FURTO_SMARRIMENTO_DETERIORAMENTO -> {
                assertEquals(DONE, pt.getState());
                assertEquals(1, pt.getEvents().size());
                assertNotNull(pt.getNextRequestIdPcretry());
            }
            case INESITO_FURTO_SMARRIMENTO_DETERIORAMENTO -> {
                assertEquals(DONE, pt.getState());
                assertEquals(2, pt.getEvents().size());
                assertNull(pt.getNextRequestIdPcretry());
            }
            case INESITO_INGIACENZA_FURTO_SMARRIMENTO_DETERIORAMENTO -> {
                assertEquals(DONE, pt.getState());
                assertEquals(3, pt.getEvents().size());
                assertNull(pt.getNextRequestIdPcretry());
            }
            case CONSEGNATO_PRESSO_GIACENZA_FASCICOLO_CHIUSO,
                 COMPIUTA_GIACENZA_PRESSO_GIACENZA_FASCICOLO_CHIUSO,
                 MANCATA_CONSEGNA_PRESSO_GIACENZA_FASCICOLO_CHIUSO -> assertValidatedDone(pt, 5, 5, null);

            case CONSEGNATO_PRESSO_GIACENZA_FASCICOLO_CHIUSO_ALLEGATI_MANCANTI,
                 CONSEGNATO_PRESSO_GIACENZA_FASCICOLO_CHIUSO_STATO_ASSENTE -> {
                assertEquals(KO, pt.getState());
                assertEquals(5, pt.getEvents().size());
                assertNull(pt.getNextRequestIdPcretry());
            }

            case CONSEGNATO_FASCICOLO_CHIUSO_AB_DUPLICATI_OK -> assertValidatedDoneSubset(pt, 6, 3, null,
                    List.of("RECRN001A", "RECRN001B", "RECRN001C"));

            case CONSEGNATO_PRESSO_GIACENZA_FASCICOLO_CHIUSO_010_DUPLICATO_OK,
                 CONSEGNATO_PRESSO_GIACENZA_FASCICOLO_CHIUSO_011_DUPLICATO_OK -> assertValidatedDoneSubset(pt, 6, 5, null,
                    List.of("RECRN010", "RECRN011", "RECRN003A", "RECRN003B", "RECRN003C"));

            case CONSEGNATO_FASCICOLO_CHIUSO_STATO_NON_INERENTE -> assertValidatedDoneSubset(pt, 4, 3, null,
                    List.of("RECRN001A","RECRN001B","RECRN001C"));

            case MANCATA_CONSEGNA_PRESSO_GIACENZA_FASCICOLO_CHIUSO_STATO_NON_INERENTE -> assertValidatedDoneSubset(pt, 7, 5, null,
                    List.of("RECRN010","RECRN011","RECRN004A","RECRN004B","RECRN004C"));

            case IRREPERIBILITA_ASSOLUTA_FASCICOLO_CHIUSO_ALLEGATI_SEPARATI -> assertValidatedDone(pt, 4, 4, "M01");

            case IRREPERIBILITA_ASSOLUTA_FASCICOLO_CHIUSO_ALLEGATI_MANCANTI -> {
                assertEquals(KO, pt.getState());
                assertEquals(3, pt.getEvents().size());
                assertNull(pt.getNextRequestIdPcretry());
            }
        }
    }

    private PcRetryResponse wireRetryIfNeeded(List<String> statusCodes, String requestId, String iun) {
        if (!statusCodes.contains("RECRN006")) return null;

        PcRetryResponse resp = new PcRetryResponse();
        if (statusCodes.size() == 1) {
            String newRequestId = "PREPARE_ANALOG_DOMICILE.IUN_" + iun + ".RECINDEX_0.ATTEMPT_0.PCRETRY_1";
            resp.setRetryFound(true);
            resp.setRequestId(newRequestId);
            resp.setParentRequestId(requestId);
            resp.setDeliveryDriverId("POSTE");
            resp.setPcRetry("PCRETRY_1");
        } else {
            resp.setRetryFound(false);
        }
        when(paperChannelClient.getPcRetry(any())).thenReturn(Mono.just(resp));
        return resp;
    }
}
