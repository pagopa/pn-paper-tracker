package it.pagopa.pn.papertracker.service.handler_step.RIR;

import it.pagopa.pn.papertracker.BaseTest;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PcRetryResponse;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.SingleStatusUpdate;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackerDryRunOutputsDAO;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsErrorsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.middleware.msclient.DataVaultClient;
import it.pagopa.pn.papertracker.middleware.msclient.PaperChannelClient;
import it.pagopa.pn.papertracker.middleware.msclient.SafeStorageClient;
import it.pagopa.pn.papertracker.middleware.queue.consumer.internal.ExternalChannelHandler;
import it.pagopa.pn.papertracker.model.EventStatusCodeEnum;
import it.pagopa.pn.papertracker.service.handler_step.TestUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsState.DONE;
import static it.pagopa.pn.papertracker.service.handler_step.RIR.TestSequenceRirEnum.OK_RETRY_RIR;
import static it.pagopa.pn.papertracker.service.handler_step.TestUtils.*;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class HandlerFactoryRirIT extends BaseTest.WithLocalStack {

    @Autowired
    private ExternalChannelHandler externalChannelHandler;
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
    @EnumSource(value = TestSequenceRirEnum.class)
    void testRirSequence(TestSequenceRirEnum seq) throws InterruptedException {

        //Arrange
        when(safeStorageClient.getSafeStoragePresignedUrl(any())).thenReturn(Mono.just("url"));
        String iun = UUID.randomUUID().toString();
        String requestId = "PREPARE_ANALOG_DOMICILE.IUN_" + iun + ".RECINDEX_0.ATTEMPT_0.PCRETRY_0";
        paperTrackingsDAO.putIfAbsent(getPaperTrackings(requestId, it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType.RIR)).block();
        List<SingleStatusUpdate> eventsToSend = prepareTest(seq, requestId);
        PcRetryResponse pcRetryResponse = wireRetryIfNeeded(seq.getStatusCodes(), requestId, iun);

        //Act
        eventsToSend.forEach(singleStatusUpdate -> {
            String messageId = UUID.randomUUID().toString();
            externalChannelHandler.handleExternalChannelMessage(singleStatusUpdate, true, messageId);
        });

        //Assert
        await().pollDelay(Duration.ofSeconds(1)).until(() -> true);

        PaperTrackings pt = paperTrackingsDAO.retrieveEntityByTrackingId(requestId).block();
        PaperTrackings ptNew = (pcRetryResponse != null && StringUtils.hasText(pcRetryResponse.getRequestId()))
                ? paperTrackingsDAO.retrieveEntityByTrackingId(pcRetryResponse.getRequestId()).block()
                : null;

        List<PaperTrackingsErrors> errors = paperTrackingsErrorsDAO.retrieveErrors(requestId).collectList().block();
        List<PaperTrackerDryRunOutputs> outputs = paperTrackerDryRunOutputsDAO.retrieveOutputEvents(requestId).collectList().block();
        List<PaperTrackerDryRunOutputs> outputsRetry = Collections.emptyList();
        if (Objects.nonNull(ptNew)) {
            outputsRetry = paperTrackerDryRunOutputsDAO.retrieveOutputEvents(ptNew.getTrackingId()).collectList().block();
        }

        assertNotNull(pt);
        verifyPaperTrackings(pt, ptNew, seq);
        verifyNewPaperTrackings(ptNew, pt, seq);
        verifyErrors(errors, seq);
        assertNotNull(outputs);
        verifyOutputs(outputs, outputsRetry, seq);
    }

    private List<SingleStatusUpdate> prepareTest(TestSequenceRirEnum seq, String requestId) {
        OffsetDateTime now = OffsetDateTime.now();
        List<String> requiredDoc = new ArrayList<>(seq.getSentDocuments());
        AtomicInteger counter = new AtomicInteger();
        AtomicInteger delay = new AtomicInteger(0);
        String productType = ProductType.RIR.getValue();

        return seq.getStatusCodes().stream().map(code -> {
            PaperProgressStatusEvent ev;
            if (ifRetrySequenceBeforeRetryEvent(seq, counter)) {
                ev = createSimpleAnalogMail(requestId, now, delay, productType);
                counter.getAndIncrement();
            } else if (ifRetrySequenceAfterRetryEvent(seq, counter)) {
                String newRequestId = requestId.substring(0, requestId.length() - 1).concat("1");
                ev = createSimpleAnalogMail(newRequestId,now.plusMinutes(1), delay, productType);
                counter.getAndIncrement();
            } else {
                ev = createSimpleAnalogMail(requestId, now, delay, productType);
            }

            String finalCode = code.replace("[NOAUTODATETIME]","");

            var conf = EventStatusCodeEnum.fromKey(finalCode);
            ev.setStatusCode(finalCode);
            ev.setStatusDescription(conf.getStatusCodeDescription());
            if (!CollectionUtils.isEmpty(conf.getDeliveryFailureCauseList())) {
                ev.setDeliveryFailureCause(conf.getDeliveryFailureCauseList().getFirst().name());
            }
            if (!CollectionUtils.isEmpty(requiredDoc)) {
                ev.setAttachments(constructAttachments(finalCode, requiredDoc));
            }
            SingleStatusUpdate msg = new SingleStatusUpdate();
            msg.setAnalogMail(ev);
            return msg;
        }).toList();
    }

    private boolean ifRetrySequenceBeforeRetryEvent(TestSequenceRirEnum seq, AtomicInteger counter) {
        return seq.equals(OK_RETRY_RIR) && counter.get() < 3;
    }

    private boolean ifRetrySequenceAfterRetryEvent(TestSequenceRirEnum seq, AtomicInteger counter) {
        return seq.equals(OK_RETRY_RIR) && counter.get() >= 3;
    }

    private void verifyOutputs(List<PaperTrackerDryRunOutputs> list, List<PaperTrackerDryRunOutputs> listRetry, TestSequenceRirEnum seq) {
        list.forEach(TestUtils::assertBaseDryRun);

        switch (seq) {
            case OK_RIR -> {
                assertEquals(7, list.size());
                assertContainsStatus(list, List.of("CON080", "CON020", "RECRI001", "RECRI002", "RECRI003A", "RECRI003B", "RECRI003C"));
                assertSameRegisteredLetter(list, 0, 1, 2, 3, 4, 5, 6);
                list.forEach(e -> {
                    if (is(e, "CONO20")) {assertEquals(1, e.getAttachments().size());assertProgress(e);}
                    if (is(e, "CON080")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRI001")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRI002")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRI003A")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRI003B")) {assertAttach(e, "AR");assertProgress(e);}
                    if (is(e, "RECRI003C")) {assertNoAttach(e);assertOk(e);assertNull(e.getDeliveryFailureCause());}
                });
            }
            case FAIL_RIR -> {
                assertEquals(7, list.size());
                assertContainsStatus(list, List.of("CON080", "CON020", "RECRI001", "RECRI002", "RECRI004A", "RECRI004B", "RECRI004C"));
                assertSameRegisteredLetter(list, 0, 1, 2, 3, 4, 5, 6);
                list.forEach(e -> {
                    if (is(e, "CON080")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "CONO20")) {assertEquals(1, e.getAttachments().size());assertProgress(e);}
                    if (is(e, "RECRI001")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRI002")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRI004A")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRI004B")) {assertAttach(e, "Plico");assertProgress(e);}
                    if (is(e, "RECRI004C")) {assertNoAttach(e);assertKo(e);assertNull(e.getDeliveryFailureCause());}
                });
            }
            case OK_RETRY_RIR -> {
                assertEquals(3, list.size());
                assertContainsStatus(list, List.of("CON080", "CON020", "RECRI005"));
                assertContainsStatus(listRetry, List.of("CON080", "CON020", "RECRI001", "RECRI002", "RECRI003A", "RECRI003B", "RECRI003C"));
                assertSameRegisteredLetter(list, 0, 1, 2);
                list.forEach(e -> {
                    if (is(e, "CON080")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "CONO20")) {assertEquals(1, e.getAttachments().size());assertProgress(e);}
                    if (is(e, "RECRI005")) {assertNoAttach(e);assertProgress(e);assertNotNull(e.getDeliveryFailureCause());}}
                );
                listRetry.forEach(e -> {
                    if (is(e, "CON080")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "CONO20")) {assertEquals(1, e.getAttachments().size());assertProgress(e);}
                    if (is(e, "RECRI001")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRI002")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRI003A")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRI003B")) {assertAttach(e, "AR");assertProgress(e);}
                    if (is(e, "RECRI003C")) {assertNoAttach(e);assertOk(e);assertNull(e.getDeliveryFailureCause());}}
                );
            }
        }
    }

    private void verifyErrors(List<PaperTrackingsErrors> errs, TestSequenceRirEnum seq) {
        switch (seq) {
            case OK_RIR, FAIL_RIR, OK_RETRY_RIR ->
                    assertEquals(0, errs.size());
        }
    }

    private void verifyNewPaperTrackings(PaperTrackings ptNew, PaperTrackings pt, TestSequenceRirEnum seq) {
        if (seq.equals(OK_RETRY_RIR)) {
            assertNotNull(ptNew);
            assertEquals(pt.getProductType(), ptNew.getProductType());
            assertEquals(PaperTrackingsState.DONE, ptNew.getState());
            assertEquals(pt.getUnifiedDeliveryDriver(), ptNew.getUnifiedDeliveryDriver());
            assertTrue(ptNew.getTrackingId().endsWith(".RECINDEX_0.ATTEMPT_0.PCRETRY_1"));
        } else {
            assertNull(ptNew);
        }
    }

    private void verifyPaperTrackings(PaperTrackings pt, PaperTrackings newPt, TestSequenceRirEnum seq) {
        List<String> events = new ArrayList<>(pt.getEvents().stream().map(Event::getStatusCode).toList());
        if (Objects.nonNull(newPt)) {
            events.addAll(newPt.getEvents().stream().map(Event::getStatusCode).toList());
        }
        List<String> cleanedSequenceStatusCode = seq.getStatusCodes().stream()
                .map(s -> s.replace("[NOAUTODATETIME]","")).toList();
        assertTrue(events.containsAll(cleanedSequenceStatusCode));

        switch (seq) {
            case OK_RIR ->
                    assertValidatedDoneSubset(pt, 7, 5, null, List.of("RECRI001", "RECRI002", "RECRI003A", "RECRI003B", "RECRI003C"),  DONE,BusinessState.DONE);
            case OK_RETRY_RIR -> assertValidatedDone(newPt, 7, 5, null, DONE,BusinessState.DONE);
            case FAIL_RIR -> assertValidatedDone(pt, 7, 5, null, DONE,BusinessState.DONE);
        }
    }

    private PcRetryResponse wireRetryIfNeeded(List<String> statusCodes, String requestId, String iun) {
        if (!statusCodes.contains("RECRI005")) return null;

        PcRetryResponse resp = new PcRetryResponse();
        String newRequestId = "PREPARE_ANALOG_DOMICILE.IUN_" + iun + ".RECINDEX_0.ATTEMPT_0.PCRETRY_1";
        resp.setRetryFound(true);
        resp.setRequestId(newRequestId);
        resp.setParentRequestId(requestId);
        resp.setDeliveryDriverId("POSTE");
        resp.setPcRetry("PCRETRY_1");

        paperTrackingsDAO.putIfAbsent(getPaperTrackings(newRequestId, it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType.RIR)).block();
        when(paperChannelClient.getPcRetry(any(), any())).thenReturn(Mono.just(resp));
        return resp;
    }
}
