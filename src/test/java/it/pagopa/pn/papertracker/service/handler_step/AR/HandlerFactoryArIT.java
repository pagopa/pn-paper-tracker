package it.pagopa.pn.papertracker.service.handler_step.AR;

import it.pagopa.pn.papertracker.BaseTest;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerValidationException;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsState.*;
import static it.pagopa.pn.papertracker.service.handler_step.AR.TestSequenceAREnum.*;
import static it.pagopa.pn.papertracker.service.handler_step.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class HandlerFactoryArIT extends BaseTest.WithLocalStack {

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
    @EnumSource(value = TestSequenceAREnum.class)
    void testARSequence(TestSequenceAREnum seq) throws InterruptedException {

        //Arrange
        when(safeStorageClient.getSafeStoragePresignedUrl(any())).thenReturn(Mono.just("url"));
        String iun = UUID.randomUUID().toString();
        String requestId = "PREPARE_ANALOG_DOMICILE.IUN_" + iun + ".RECINDEX_0.ATTEMPT_0.PCRETRY_0";
        paperTrackingsDAO.putIfAbsent(getPaperTrackings(requestId)).block();
        List<SingleStatusUpdate> eventsToSend = prepareTest(seq, requestId);
        PcRetryResponse pcRetryResponse = wireRetryIfNeeded(seq.getStatusCodes(), requestId, iun);

        //Act
        if(seq.equals(FAIL_COMPIUTA_GIACENZA_AR) || seq.equals(KO_AR_NO_EVENT_B)) {
            Assertions.assertThrows(PnPaperTrackerValidationException.class, () -> eventsToSend.forEach(singleStatusUpdate -> {
                String messageId = UUID.randomUUID().toString();
                externalChannelHandler.handleExternalChannelMessage(singleStatusUpdate, true, messageId);
            }));
        }else{
          eventsToSend.forEach(singleStatusUpdate -> {
              String messageId = UUID.randomUUID().toString();
              externalChannelHandler.handleExternalChannelMessage(singleStatusUpdate, true, messageId);
          });
        };

        //Assert
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

    private List<SingleStatusUpdate> prepareTest(TestSequenceAREnum seq, String requestId) {
        OffsetDateTime now = OffsetDateTime.now();
        List<String> requiredDoc = new ArrayList<>(seq.getSentDocuments());
        AtomicInteger counter = new AtomicInteger();
        AtomicInteger delay = new AtomicInteger(0);
        return seq.getStatusCodes().stream().map(code -> {
            PaperProgressStatusEvent ev;
            if (ifRetrySequenceBeforeRetryEvent(seq, counter)) {
                ev = createSimpleAnalogMail(requestId, now, delay);
                counter.getAndIncrement();
            } else if (ifRetrySequenceAfterRetryEvent(seq, counter)) {
                String newRequestId = requestId.substring(0, requestId.length() - 1).concat("1");
                ev = createSimpleAnalogMail(newRequestId,now.plusMinutes(1), delay);
                counter.getAndIncrement();
            } else {
                ev = createSimpleAnalogMail(requestId, now, delay);
            }
            //replace statusDateTime only for A,B,C events
            if (code.endsWith("A") || code.endsWith("B") || code.endsWith("C")) {
                ev.setStatusDateTime(ev.getStatusDateTime().plusMinutes(2));
            }
            if(code.endsWith("[NOAUTODATETIME]")){
                ev.setStatusDateTime(ev.getStatusDateTime().plusMinutes(5));
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

    private boolean ifRetrySequenceBeforeRetryEvent(TestSequenceAREnum seq, AtomicInteger counter) {
        return (seq.equals(FAIL_CON996_PCRETRY_FURTO_AR) && counter.get() < 4) ||
                ((seq.equals(OKNonRendicontabile_AR) || seq.equals(OK_RETRY_AR)) && counter.get() < 3);
    }

    private boolean ifRetrySequenceAfterRetryEvent(TestSequenceAREnum seq, AtomicInteger counter) {
        return (seq.equals(FAIL_CON996_PCRETRY_FURTO_AR) && counter.get() >= 4) ||
                ((seq.equals(OKNonRendicontabile_AR) || seq.equals(OK_RETRY_AR)) && counter.get() >= 3);
    }

    private void verifyOutputs(List<PaperTrackerDryRunOutputs> list, List<PaperTrackerDryRunOutputs> listRetry, TestSequenceAREnum seq) {
        list.forEach(TestUtils::assertBaseDryRun);

        switch (seq) {
            case OK_AR -> {
                assertEquals(5, list.size());
                assertContainsStatus(list, List.of("CON080", "CON020", "RECRN001A", "RECRN001B", "RECRN001C"));
                assertSameRegisteredLetter(list, 0, 1, 2, 3, 4);
                list.forEach(e -> {
                    if (is(e, "RECRN001A")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "CONO20")) {assertEquals(1, e.getAttachments().size());assertProgress(e);}
                    if (is(e, "CON080")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRN001B")) {assertAttach(e, "AR");assertProgress(e);}
                    if (is(e, "RECRN001C")) {assertNoAttach(e);assertOk(e);assertNull(e.getDeliveryFailureCause());}
                });
            }
            case OKCausaForzaMaggiore_AR -> {
                assertEquals(6, list.size());
                assertContainsStatus(list, seq.getStatusCodes());
                assertSameRegisteredLetter(list, 0, 1, 2, 3, 4, 5);
                list.forEach(e -> {
                    if (is(e, "RECRN001A")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "CONO20")) {assertEquals(1, e.getAttachments().size());assertProgress(e);}
                    if (is(e, "RECRN015")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "CON080")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRN001B")) {assertAttach(e, "AR");assertProgress(e);}
                    if (is(e, "RECRN001C")) {assertNoAttach(e);assertOk(e);assertNull(e.getDeliveryFailureCause());}
                });
            }
            case OK_AR_NOT_ORDERED -> {
                assertEquals(5, list.size());
                assertContainsStatus(list, List.of("CON080", "CON020", "RECRN001A", "RECRN001B", "RECRN001C"));
                assertSameRegisteredLetter(list, 0, 1, 2, 3, 4);
                list.forEach(e -> {
                    if (is(e, "RECRN001A")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "CONO20")) {assertEquals(1, e.getAttachments().size());assertProgress(e);}
                    if (is(e, "CON080")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRN001B")) {assertAttach(e, "AR");assertProgress(e);}
                    if (is(e, "RECRN001C")) {assertNoAttach(e);assertOk(e);assertNull(e.getDeliveryFailureCause());}
                });
            }
            case OK_GIACENZA_AR -> {
                assertEquals(7, list.size());
                assertContainsStatus(list, seq.getStatusCodes());
                assertSameRegisteredLetter(list, 0, 1, 2, 3, 4, 5, 6);
                list.forEach(e -> {
                    if (is(e, "RECRN003A")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "CONO20")) {assertEquals(1, e.getAttachments().size());assertProgress(e);}
                    if (is(e, "RECRN010")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRN011")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "CON018")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "CON080")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRN003B")) {assertAttach(e, "AR");assertProgress(e);}
                    if (is(e, "RECRN003C")) {assertNoAttach(e);assertOk(e);assertNull(e.getDeliveryFailureCause());}
                });
            }
            case FAIL_AR -> {
                assertEquals(5, list.size());
                assertContainsStatus(list, seq.getStatusCodes());
                assertSameRegisteredLetter(list, 0, 1, 2, 3, 4);
                list.forEach(e -> {
                    if (is(e, "RECRN001A")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "CONO20")) {assertEquals(1, e.getAttachments().size());assertProgress(e);}
                    if (is(e, "CON080")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRN001B")) {assertAttach(e, "Plico");assertProgress(e);}
                    if (is(e, "RECRN001C")) {assertNoAttach(e);assertOk(e);assertNull(e.getDeliveryFailureCause());}
                });
            }
            case OK_GIACENZA_AR_2, OK_GIACENZA_AR_3 -> {
                List<String> filteredStatusCode = seq.getStatusCodes().stream().filter(s -> !s.equals("CON018")).distinct().toList();
                assertEquals(7, list.size());
                assertContainsStatus(list, filteredStatusCode);
                assertSameRegisteredLetter(list, 0, 1, 2, 3, 4, 5, 6);
                list.forEach(e -> {
                    if (is(e, "RECRN003A")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "CONO20")) {assertEquals(1, e.getAttachments().size());assertProgress(e);}
                    if (is(e, "RECRN010")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRN011")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "CON080")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRN003B")) {assertAttach(e, "AR");assertProgress(e);}
                    if (is(e, "RECRN003C")) {assertNoAttach(e);assertOk(e);assertNull(e.getDeliveryFailureCause());}
                });
            }
            case FAIL_IRREPERIBILE_AR -> {
                assertEquals(5, list.size());
                assertContainsStatus(list, seq.getStatusCodes());
                assertSameRegisteredLetter(list, 0, 1, 2, 3, 4);
                list.forEach(e -> {
                    if (is(e, "RECRN002D")) {assertNoAttach(e);assertNotNull(e.getDeliveryFailureCause());assertProgress(e);}
                    if (is(e, "RECRN002E")) {assertAttachAnyOf(e, "Plico", "Indagine");assertNull(e.getDeliveryFailureCause());assertProgress(e);}
                    if (is(e, "RECRN002F")) {assertNoAttach(e);assertNull(e.getDeliveryFailureCause());assertKo(e);}
                    if (is(e, "CONO20")) {assertEquals(1, e.getAttachments().size());assertProgress(e);}
                    if (is(e, "CON080")) {assertNoAttach(e);assertProgress(e);assertNull(e.getDeliveryFailureCause());}
                });
            }
            case FAIL_COMPIUTA_GIACENZA_AR -> {
                assertEquals(5, list.size());
                assertContainsStatus(list,List.of("CON080","CON020","RECRN010", "RECRN005A", "RECRN005B"));
                assertSameRegisteredLetter(list, 0, 1, 2, 3, 4);
                list.forEach(e -> {
                    if (is(e, "CONO20")) {assertEquals(1, e.getAttachments().size());assertProgress(e);}
                    if (is(e, "CON080")) {assertNoAttach(e);assertProgress(e);assertNull(e.getDeliveryFailureCause());}
                    if (is(e, "RECRN010")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRN005A")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRN005B")) {assertAttach(e, "Plico");assertProgress(e);}
                });
            }
            case FAIL_GIACENZA_AR -> {
                assertEquals(7, list.size());
                assertContainsStatus(list, seq.getStatusCodes());
                assertSameRegisteredLetter(list, 0, 1, 2, 3, 4, 5, 6);
                list.forEach(e -> {
                    if (is(e, "RECRN010")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRN011")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRN004A")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRN004B")) {assertAttach(e, "Plico");assertProgress(e);}
                    if (is(e, "RECRN004C")) {assertNoAttach(e);assertOk(e);}
                    if (is(e, "CONO20")) {assertEquals(1, e.getAttachments().size());assertProgress(e);}
                    if (is(e, "CON080")) {assertNoAttach(e);assertProgress(e);assertNull(e.getDeliveryFailureCause());}
                });
            }
            case KO_AR_NO_EVENT_B -> {
                assertEquals(3, list.size());
                assertContainsStatus(list, List.of("RECRN001A", "CON020", "CON080"));
                assertSameRegisteredLetter(list, 0, 1, 2);
                list.forEach(e -> {
                    if (is(e, "RECRN001A")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "CON020")) {assertEquals(1, e.getAttachments().size());assertProgress(e);}
                    if (is(e, "CON080")) {assertNoAttach(e);assertProgress(e);}
                });
            }
            case OK_AR_BAD_EVENT -> {
                assertEquals(6, list.size());
                assertContainsStatus(list, List.of("CON080", "CON020", "RECRN001A", "RECRN002A", "RECRN001B", "RECRN001C"));
                assertSameRegisteredLetter(list, 0, 1, 2, 3, 4, 5);
                list.forEach(e -> {
                    if (is(e, "RECRN001A")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRN002A")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "CONO20")) {assertEquals(1, e.getAttachments().size());assertProgress(e);}
                    if (is(e, "CON080")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRN001B")) {assertAttach(e, "AR");assertProgress(e);}
                    if (is(e, "RECRN001C")) {assertNoAttach(e);assertOk(e);assertNull(e.getDeliveryFailureCause());}
                });
            }
//            case FAIL_DISCOVERY_AR -> {
//                assertEquals(11, list.size());
//                assertContainsStatus(list, seq.getStatusCodes());
//                assertSameRegisteredLetter(list, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
//                list.forEach(e -> {
//                    if (is(e, "RECRN001A")) {assertNoAttach(e);assertProgress(e);}
//                    if (is(e, "CONO20")) {assertEquals(1, e.getAttachments().size());assertProgress(e);}
//                    if (is(e, "CON080")) {assertNoAttach(e);assertProgress(e);}
//                    if (is(e, "RECRN001B")) {assertAttach(e, "AR");assertProgress(e);}
//                    if (is(e, "RECRN001C")) {assertNoAttach(e);assertOk(e);assertNull(e.getDeliveryFailureCause());}
//                    if (is(e, "RECRN002D")) {assertNoAttach(e);assertNotNull(e.getDeliveryFailureCause());assertProgress(e);}
//                    if (is(e, "RECRN002E")) {assertAttachAnyOf(e, "Plico", "Indagine");assertNull(e.getDeliveryFailureCause());assertProgress(e);}
//                    if (is(e, "RECRN002F")) {assertNoAttach(e);assertNull(e.getDeliveryFailureCause());assertKo(e);}
//                });
//                Assertions.assertEquals(2, list.stream()
//                        .filter(paperTrackerDryRunOutputs -> paperTrackerDryRunOutputs.getStatusDetail().equalsIgnoreCase("RECRN002E"))
//                        .toList()
//                        .size());
//            }
            case FAIL_CON996_PCRETRY_FURTO_AR -> {
                assertEquals(4, list.size());
                assertEquals(5, listRetry.size());
                assertContainsStatus(list, List.of("CON996", "CON080", "CON020", "RECRN006"));
                assertContainsStatus(listRetry, List.of("CON080", "CON020", "RECRN001A", "RECRN001B", "RECRN001C"));
                assertSameRegisteredLetter(listRetry, 0, 1, 2, 3, 4);
                list.forEach(e -> {
                    if (is(e, "CON996")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "CONO20")) {assertEquals(1, e.getAttachments().size());assertProgress(e);}
                    if (is(e, "CON080")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRN006")) {assertNoAttach(e);assertProgress(e);}
                });
                listRetry.forEach(e -> {
                    if (is(e, "RECRN001A")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "CONO20")) {assertEquals(1, e.getAttachments().size());assertProgress(e);}
                    if (is(e, "CON080")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRN001B")) {assertAttach(e, "AR");assertProgress(e);}
                    if (is(e, "RECRN001C")) {assertNoAttach(e);assertOk(e);assertNull(e.getDeliveryFailureCause());}
                });
            }
            case OK_RETRY_AR -> {
                assertEquals(3, list.size());
                assertContainsStatus(list, List.of("CON080", "CON020", "RECRN006"));
                assertContainsStatus(listRetry, List.of("CON080", "CON020", "RECRN001A", "RECRN001B", "RECRN001C"));
                assertSameRegisteredLetter(list, 0, 1, 2);
                list.forEach(e -> {
                    if (is(e, "CONO20")) {assertEquals(1, e.getAttachments().size());assertProgress(e);}
                    if (is(e, "CON080")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRN006")) {assertNoAttach(e);assertProgress(e);}
                });
                listRetry.forEach(e -> {
                    if (is(e, "RECRN001A")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "CONO20")) {assertEquals(1, e.getAttachments().size());assertProgress(e);}
                    if (is(e, "CON080")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRN001B")) {assertAttach(e, "AR");assertProgress(e);}
                    if (is(e, "RECRN001C")) {assertNoAttach(e);assertOk(e);assertNull(e.getDeliveryFailureCause());}
                });
            }
            case OKNonRendicontabile_AR -> {
                assertEquals(3, list.size());
                assertContainsStatus(list, List.of("CON080", "CON020", "RECRN013"));
                assertContainsStatus(listRetry, List.of("CON080", "CON020", "RECRN001A", "RECRN001B", "RECRN001C"));
                assertSameRegisteredLetter(list, 0, 1, 2);
                list.forEach(e -> {
                    if (is(e, "CONO20")) {assertEquals(1, e.getAttachments().size());assertProgress(e);}
                    if (is(e, "CON080")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRN013")) {assertNoAttach(e);assertProgress(e);}
                });
                listRetry.forEach(e -> {
                    if (is(e, "RECRN001A")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "CONO20")) {assertEquals(1, e.getAttachments().size());assertProgress(e);}
                    if (is(e, "CON080")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRN001B")) {assertAttach(e, "AR");assertProgress(e);}
                    if (is(e, "RECRN001C")) {assertNoAttach(e);assertOk(e);assertNull(e.getDeliveryFailureCause());}
                });
            }
            case OK_AR_INVALID_DATETIME -> {
                assertEquals(4, list.size());
                assertContainsStatus(list, List.of("CON080", "CON020", "RECRN001A", "RECRN001B"));
                assertSameRegisteredLetter(list, 0, 1, 2, 3);
                list.forEach(e -> {
                    if (is(e, "RECRN001A")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "CONO20")) {assertEquals(1, e.getAttachments().size());assertProgress(e);}
                    if (is(e, "CON080")) {assertNoAttach(e);assertProgress(e);}
                    if (is(e, "RECRN001B")) {assertAttach(e, "AR");assertProgress(e);}
                });
            }

        }
    }

    private void verifyErrors(List<PaperTrackingsErrors> errs, TestSequenceAREnum seq) {
        switch (seq) {
            case OK_AR, OK_GIACENZA_AR, FAIL_GIACENZA_AR, OKCausaForzaMaggiore_AR,
                 FAIL_IRREPERIBILE_AR, OK_RETRY_AR,
                 FAIL_AR, OK_AR_BAD_EVENT, /*FAIL_DISCOVERY_AR,*/  OKNonRendicontabile_AR, KO_AR_NO_EVENT_B, FAIL_COMPIUTA_GIACENZA_AR ->
                    assertEquals(0, errs.size());
            case OK_AR_NOT_ORDERED -> assertSingleWarning(errs, ErrorCategory.DUPLICATED_EVENT, FlowThrow.DUPLICATED_EVENT_VALIDATION, "RECRN001A");
            case OK_GIACENZA_AR_2 -> assertSingleWarning(errs, ErrorCategory.DUPLICATED_EVENT, FlowThrow.DUPLICATED_EVENT_VALIDATION, "RECRN010");
            case OK_GIACENZA_AR_3 -> assertSingleWarning(errs, ErrorCategory.DUPLICATED_EVENT, FlowThrow.DUPLICATED_EVENT_VALIDATION, "RECRN011");
            case FAIL_CON996_PCRETRY_FURTO_AR ->
                    assertSingleWarning(errs, ErrorCategory.NOT_RETRYABLE_EVENT_ERROR, FlowThrow.NOT_RETRYABLE_EVENT_HANDLER, "Scartato PDF");
            case OK_AR_TIMESTAMP_ERR -> {
                assertWarning(errs.getFirst(), ErrorCategory.DUPLICATED_EVENT, FlowThrow.DUPLICATED_EVENT_VALIDATION, "RECRN001B");
                assertError(errs.get(1), ErrorCategory.DATE_ERROR, FlowThrow.SEQUENCE_VALIDATION, "Invalid business timestamps");
                assertWarning(errs.getLast(), ErrorCategory.DUPLICATED_EVENT, FlowThrow.DUPLICATED_EVENT_VALIDATION, "RECRN001A");
            }
            case OK_AR_INVALID_DATETIME -> assertSingleError(errs, ErrorCategory.DATE_ERROR, FlowThrow.SEQUENCE_VALIDATION, "Invalid business timestamps");

        }
    }

    private void verifyNewPaperTrackings(PaperTrackings ptNew, PaperTrackings pt, TestSequenceAREnum seq) {
        if (seq.equals(FAIL_CON996_PCRETRY_FURTO_AR) || seq.equals(OK_RETRY_AR) || seq.equals(OKNonRendicontabile_AR)) {
            assertNotNull(ptNew);
            assertEquals(pt.getProductType(), ptNew.getProductType());
            assertEquals(PaperTrackingsState.DONE, ptNew.getState());
            assertEquals(pt.getUnifiedDeliveryDriver(), ptNew.getUnifiedDeliveryDriver());
            assertTrue(ptNew.getTrackingId().endsWith(".RECINDEX_0.ATTEMPT_0.PCRETRY_1"));
        } else {
            assertNull(ptNew);
        }
    }

    private void verifyPaperTrackings(PaperTrackings pt, PaperTrackings newPt, TestSequenceAREnum seq) {
        assertNull(pt.getOcrRequestId());
        List<String> events = new ArrayList<>(pt.getEvents().stream().map(Event::getStatusCode).toList());
        if (Objects.nonNull(newPt)) {
            events.addAll(newPt.getEvents().stream().map(Event::getStatusCode).toList());
        }
        List<String> cleanedSequenceStatusCode = seq.getStatusCodes().stream()
                        .map(s -> s.replace("[NOAUTODATETIME]","")).toList();
        assertTrue(events.containsAll(cleanedSequenceStatusCode));

        switch (seq) {
            case OK_AR, OKCausaForzaMaggiore_AR ->
                    assertValidatedDoneSubset(pt, 6, 3, null, List.of("RECRN001A", "RECRN001B", "RECRN001C"));
            case FAIL_CON996_PCRETRY_FURTO_AR -> assertValidatedDone(newPt, 6, 3, null);
            case OK_RETRY_AR, OKNonRendicontabile_AR -> assertValidatedDone(newPt, 5, 3, null);
            case OK_AR_NOT_ORDERED -> assertValidatedDone(pt, 7, 3, null);
            case OK_GIACENZA_AR -> assertValidatedDone(pt, 7, 5, null);
            case FAIL_IRREPERIBILE_AR -> assertValidatedDone(pt, 5, 3, "M01");
            case OK_AR_INVALID_DATETIME -> {
                assertEquals(KO, pt.getState());
                assertEquals(6, pt.getEvents().size());
                assertNull(pt.getNextRequestIdPcretry());
            }
            case FAIL_COMPIUTA_GIACENZA_AR -> {
                assertEquals(AWAITING_FINAL_STATUS_CODE, pt.getState());
                assertEquals(6, pt.getEvents().size());
                assertNull(pt.getNextRequestIdPcretry());
            }
            case OK_GIACENZA_AR_2, OK_GIACENZA_AR_3 -> assertValidatedDone(pt, 9, 5, null);

            case FAIL_AR -> assertValidatedDone(pt, 5, 3, "M02");
            case KO_AR_NO_EVENT_B -> {
                assertEquals(AWAITING_FINAL_STATUS_CODE, pt.getState());
                assertEquals(5, pt.getEvents().size());
                assertNull(pt.getNextRequestIdPcretry());
            }
            case FAIL_GIACENZA_AR -> {
                assertEquals(DONE, pt.getState());
                assertEquals(7, pt.getEvents().size());
                assertEquals(5, pt.getPaperStatus().getValidatedEvents().size());
                assertNull(pt.getNextRequestIdPcretry());
            }
            case OK_AR_BAD_EVENT ->
                    assertValidatedDoneSubset(pt, 7, 3, null, List.of("RECRN001A", "RECRN001B", "RECRN001C"));
//            case FAIL_DISCOVERY_AR ->
//                    assertValidatedDoneSubset(pt, 10, 3, "M01", List.of("RECRN001A", "RECRN001B", "RECRN001C"));
            case OK_AR_TIMESTAMP_ERR -> assertValidatedDone(pt,11, 3, null);
        }
    }

    private PcRetryResponse wireRetryIfNeeded(List<String> statusCodes, String requestId, String iun) {
        if (!statusCodes.contains("RECRN006") && !statusCodes.contains("RECRN013")) return null;

        PcRetryResponse resp = new PcRetryResponse();
        String newRequestId = "PREPARE_ANALOG_DOMICILE.IUN_" + iun + ".RECINDEX_0.ATTEMPT_0.PCRETRY_1";
        resp.setRetryFound(true);
        resp.setRequestId(newRequestId);
        resp.setParentRequestId(requestId);
        resp.setDeliveryDriverId("POSTE");
        resp.setPcRetry("PCRETRY_1");

        when(paperChannelClient.getPcRetry(any())).thenReturn(Mono.just(resp));
        return resp;
    }
}
