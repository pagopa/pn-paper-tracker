package it.pagopa.pn.papertracker.service.handler_step._890;

import it.pagopa.pn.papertracker.BaseTest;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.SingleStatusUpdate;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PcRetryResponse;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.ProductType;
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
import static it.pagopa.pn.papertracker.service.handler_step.TestUtils.*;
import static it.pagopa.pn.papertracker.service.handler_step._890.TestSequence890Enum.*;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class HandlerFactory890IT extends BaseTest.WithLocalStack {

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
    @EnumSource(value = TestSequence890Enum.class)
    void test890Sequence(TestSequence890Enum seq) throws InterruptedException {
        //Arrange
        when(safeStorageClient.getSafeStoragePresignedUrl(any())).thenReturn(Mono.just("url"));
        String iun = UUID.randomUUID().toString();
        String requestId = "PREPARE_ANALOG_DOMICILE.IUN_" + iun + ".RECINDEX_0.ATTEMPT_0.PCRETRY_0";
        paperTrackingsDAO.putIfAbsent(getPaperTrackings(requestId, it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType._890)).block();
        List<SingleStatusUpdate> eventsToSend = prepareTest(seq, requestId);

        PcRetryResponse r1 = buildPcRetryResponse(requestId, iun, 1);
        PcRetryResponse r2 = buildPcRetryResponse(r1.getRequestId(), iun, 2);

        when(paperChannelClient.getPcRetry(any(), eq(true)))
                .thenReturn(Mono.just(r1));


     /*   if(seq.equals(FURTO) || seq.equals(NON_RENDICONTABILE) || seq.equals(CAUSA_FORZA_MAGGIORE)){
            paperTrackingsDAO.putIfAbsent(getPaperTrackings(r1.getRequestId())).block();
            when(paperChannelClient.getPcRetry(any(), eq(false)))
                    .thenReturn(Mono.just(r1));
        }
*/

        eventsToSend.forEach(singleStatusUpdate -> {
            String messageId = UUID.randomUUID().toString();
            externalChannelHandler.handleExternalChannelMessage(singleStatusUpdate, true, messageId);
        });

        //Assert
        await().pollDelay(Duration.ofSeconds(1)).until(() -> true);
        PaperTrackings pt = paperTrackingsDAO.retrieveEntityByTrackingId(requestId).block();
        PaperTrackings ptNew = StringUtils.hasText(r1.getRequestId())
                ? paperTrackingsDAO.retrieveEntityByTrackingId(r1.getRequestId()).block()
                : null;

        PaperTrackings ptNew2 = StringUtils.hasText(r2.getRequestId())
                ? paperTrackingsDAO.retrieveEntityByTrackingId(r2.getRequestId()).block()
                : null;

        List<PaperTrackingsErrors> errors = paperTrackingsErrorsDAO.retrieveErrors(requestId).collectList().block();
        List<PaperTrackerDryRunOutputs> outputs = paperTrackerDryRunOutputsDAO.retrieveOutputEvents(requestId).collectList().block();
        List<PaperTrackerDryRunOutputs> outputsRetry = Collections.emptyList();
        List<PaperTrackerDryRunOutputs> outputsRetry2 = Collections.emptyList();
        if (Objects.nonNull(ptNew)) {
            outputsRetry = paperTrackerDryRunOutputsDAO.retrieveOutputEvents(ptNew.getTrackingId()).collectList().block();
        }
        if (Objects.nonNull(ptNew2)) {
            outputsRetry2 = paperTrackerDryRunOutputsDAO.retrieveOutputEvents(ptNew2.getTrackingId()).collectList().block();
        }

        assertNotNull(pt);
        verifyPaperTrackings(pt, ptNew, ptNew2, seq);
        //verifyNewPaperTrackings(ptNew, ptNew2, pt, seq);
        verifyErrors(errors, seq);
        assertNotNull(outputs);
        verifyOutputs(outputs, outputsRetry, outputsRetry2, seq);
    }

    private List<SingleStatusUpdate> prepareTest(TestSequence890Enum seq, String requestId) {
        OffsetDateTime now = OffsetDateTime.now();
        List<String> requiredDoc = new ArrayList<>(seq.getSentDocuments());
        AtomicInteger counter = new AtomicInteger();
        AtomicInteger delay = new AtomicInteger(0);
        String productType = ProductType._890.getValue();

        return seq.getStatusCodes().stream().map(code -> {
            PaperProgressStatusEvent ev;
       /*     if (ifRetrySequenceBeforeRetryEvent(seq, counter)) {
                ev = createSimpleAnalogMail(requestId, now, delay, productType);
                counter.getAndIncrement();
            } else if (ifRetrySequenceAfterRetryEvent(seq, counter)) {
                String newRequestId = requestId.substring(0, requestId.length() - 1).concat("1");
                ev = createSimpleAnalogMail(newRequestId, now.plusMinutes(1), delay, productType);
                counter.getAndIncrement();
            } else {*/
                ev = createSimpleAnalogMail(requestId, now, delay, productType);
           // }
            //replace statusDateTime only for A,B,C events
            if (code.endsWith("A") || code.endsWith("B") || code.endsWith("C")) {
                ev.setStatusDateTime(ev.getStatusDateTime().plusMinutes(2));
            }
            if (code.endsWith("[NOAUTODATETIME]")) {
                ev.setStatusDateTime(ev.getStatusDateTime().plusMinutes(5));
            }

            String finalCode = code.replace("[NOAUTODATETIME]", "");

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

/*    private boolean ifRetrySequenceBeforeRetryEvent(TestSequence890Enum seq, AtomicInteger counter) {
        return (seq.equals(CAUSA_FORZA_MAGGIORE) || seq.equals(FURTO) || seq.equals(NON_RENDICONTABILE)) && counter.get() < 3;
    }

    private boolean ifRetrySequenceAfterRetryEvent(TestSequence890Enum seq, AtomicInteger counter) {
        return (seq.equals(CAUSA_FORZA_MAGGIORE) || seq.equals(FURTO) || seq.equals(NON_RENDICONTABILE)) && counter.get() >= 3;
    }*/

    private void verifyOutputs(List<PaperTrackerDryRunOutputs> list, List<PaperTrackerDryRunOutputs> listRetry, List<PaperTrackerDryRunOutputs> listRetry2, TestSequence890Enum seq) {
        list.forEach(TestUtils::assertBaseDryRun);

        switch (seq) {
            case CONSEGNATO -> {
                assertEquals(5, list.size());
                assertContainsStatus(list, List.of("CON080", "CON020", "RECAG001A", "RECAG001B", "RECAG001C"));
                assertSameRegisteredLetter(list, 2, 3, 4);
                list.forEach(e -> {
                    if (is(e, "RECAG001A")) { assertNoAttach(e);assertProgress(e);}
                    if (is(e, "CONO20")) {assertEquals(1, e.getAttachments().size());assertProgress(e);}
                    if (is(e, "CON080")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECAG001B")) {assertAttach(e, "23L");assertProgress(e);}
                    if (is(e, "RECAG001C")) {assertNoAttach(e);assertOk(e);assertNull(e.getDeliveryFailureCause());}
                });
            }
        }
    }

    private void verifyErrors(List<PaperTrackingsErrors> errs, TestSequence890Enum seq) {
        switch (seq) {
            case CONSEGNATO -> assertEquals(0, errs.size());
        }
    }

/*    private void verifyNewPaperTrackings(PaperTrackings ptNew, PaperTrackings ptNew2, PaperTrackings pt, TestSequence890Enum seq) {
        if (seq.equals(FURTO) || seq.equals(NON_RENDICONTABILE) || seq.equals(CAUSA_FORZA_MAGGIORE)) {
            assertNotNull(ptNew);
            assertEquals(pt.getProductType(), ptNew.getProductType());
            assertEquals(DONE, ptNew.getState());
            assertEquals(pt.getUnifiedDeliveryDriver(), ptNew.getUnifiedDeliveryDriver());
            assertTrue(ptNew.getTrackingId().endsWith(".RECINDEX_0.ATTEMPT_0.PCRETRY_1"));
        } else {
            assertNull(ptNew);
        }
    }*/

    private void verifyPaperTrackings(PaperTrackings pt, PaperTrackings newPt, PaperTrackings newPt2, TestSequence890Enum seq) {
        List<String> events = new ArrayList<>(pt.getEvents().stream().map(Event::getStatusCode).toList());
        if (Objects.nonNull(newPt)) {
            events.addAll(newPt.getEvents().stream().map(Event::getStatusCode).toList());
        }
        if (Objects.nonNull(newPt2)) {
            events.addAll(newPt2.getEvents().stream().map(Event::getStatusCode).toList());
        }
        List<String> cleanedSequenceStatusCode = seq.getStatusCodes().stream()
                .map(s -> s.replace("[NOAUTODATETIME]", "")).toList();
        assertTrue(events.containsAll(cleanedSequenceStatusCode));

        switch (seq) {
            case CONSEGNATO ->
                    assertValidatedDoneSubset(pt, 6, 3, null, List.of("RECAG001A", "RECAG001B", "RECAG001C"));
        }
    }

    private PcRetryResponse buildPcRetryResponse(String parentRequestId, String iun, int pcRetry) {
        PcRetryResponse resp = new PcRetryResponse();
        String newRequestId = "PREPARE_ANALOG_DOMICILE.IUN_" + iun + ".RECINDEX_0.ATTEMPT_0.PCRETRY_" + pcRetry;
        resp.setRetryFound(true);
        resp.setRequestId(newRequestId);
        resp.setParentRequestId(parentRequestId);
        resp.setDeliveryDriverId("POSTE");
        resp.setPcRetry("PCRETRY_" + pcRetry);
        return resp;
    }
}
