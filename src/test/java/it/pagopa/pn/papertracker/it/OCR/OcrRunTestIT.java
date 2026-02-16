/*
package it.pagopa.pn.papertracker.service.handler_step.generic;

import it.pagopa.pn.papertracker.BaseTest;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.SingleStatusUpdate;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackerDryRunOutputsDAO;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsErrorsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackerDryRunOutputs;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsErrors;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.middleware.msclient.DataVaultClient;
import it.pagopa.pn.papertracker.middleware.msclient.PaperChannelClient;
import it.pagopa.pn.papertracker.middleware.msclient.SafeStorageClient;
import it.pagopa.pn.papertracker.middleware.queue.consumer.internal.ExternalChannelHandler;
import it.pagopa.pn.papertracker.model.DeliveryFailureCauseEnum;
import it.pagopa.pn.papertracker.model.EventStatusCodeEnum;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static it.pagopa.pn.papertracker.service.handler_step.TestUtils.*;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class OcrRunTestIT extends BaseTest.WithLocalStack {

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
    @EnumSource(value = TestOcrRunEnum.class)
    void testSequence(TestOcrRunEnum seq) {
        //Arrange
        when(safeStorageClient.getSafeStoragePresignedUrl(any())).thenReturn(Mono.just("url"));
        String iun = UUID.randomUUID().toString();
        String requestId = "PREPARE_ANALOG_DOMICILE.IUN_" + iun + ".RECINDEX_0.ATTEMPT_0.PCRETRY_0";
        paperTrackingsDAO.putIfAbsent(getPaperTrackings(requestId, it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType.AR)).block();
        List<SingleStatusUpdate> eventsToSend = prepareTest(seq, requestId);

        //Act
        eventsToSend.forEach(singleStatusUpdate -> {
            String messageId = UUID.randomUUID().toString();
            externalChannelHandler.handleExternalChannelMessage(singleStatusUpdate, true, null, messageId, null);
        });

        //Assert
        await().pollDelay(Duration.ofSeconds(1)).until(() -> true);
        PaperTrackings pt = paperTrackingsDAO.retrieveEntityByTrackingId(requestId).block();
        List<PaperTrackingsErrors> errors = paperTrackingsErrorsDAO.retrieveErrors(requestId).collectList().block();
        List<PaperTrackerDryRunOutputs> outputs = paperTrackerDryRunOutputsDAO.retrieveOutputEvents(requestId).collectList().block();

        assertNotNull(pt);
        verifyPaperTrackings(pt, seq);
        verifyErrors(errors, seq);
        assertNotNull(outputs);
        verifyOutputs(outputs, seq);
    }

    private void verifyOutputs(List<PaperTrackerDryRunOutputs> outputs, TestOcrRunEnum seq) {
        switch (seq){
            case TEST_OCR_RUN_890 -> {
                assertNotNull(outputs);
            }
            case TEST_OCR_RUN_890_WITHOUT_RECAG012 -> {
                assertNotNull(outputs);
            }
            case TEST_OCR_RUN_890_WITHOUT_RECAG012_NO_REQUIRED_ATTACHMENTS -> {
                assertNotNull(outputs);
            }
            case TEST_OCR_RUN_890_RECAG012_BEFORE_ATTACHMENTS -> {
                assertNotNull(outputs);
            }
            case TEST_OCR_RUN_890_RECAG012_AFTER_ATTACHMENTS -> {
                assertNotNull(outputs);
            }
        }
    }

    private void verifyErrors(List<PaperTrackingsErrors> errors, TestOcrRunEnum seq) {
        switch (seq){
            case TEST_OCR_RUN_890 -> {
                assertNull(errors);
            }
            case TEST_OCR_RUN_890_WITHOUT_RECAG012 -> {
                assertNull(errors);
            }
            case TEST_OCR_RUN_890_WITHOUT_RECAG012_NO_REQUIRED_ATTACHMENTS -> {
                assertNull(errors);
            }
            case TEST_OCR_RUN_890_RECAG012_BEFORE_ATTACHMENTS -> {
                assertNull(errors);
            }
            case TEST_OCR_RUN_890_RECAG012_AFTER_ATTACHMENTS -> {
                assertNull(errors);
            }
        }
    }

    private void verifyPaperTrackings(PaperTrackings pt, TestOcrRunEnum seq) {
        switch (seq){
            case TEST_OCR_RUN_890 -> {
                assertNotNull(pt);
            }
            case TEST_OCR_RUN_890_WITHOUT_RECAG012 -> {
                assertNotNull(pt);
            }
            case TEST_OCR_RUN_890_WITHOUT_RECAG012_NO_REQUIRED_ATTACHMENTS -> {
                assertNotNull(pt);
            }
            case TEST_OCR_RUN_890_RECAG012_BEFORE_ATTACHMENTS -> {
                assertNotNull(pt);
            }
            case TEST_OCR_RUN_890_RECAG012_AFTER_ATTACHMENTS -> {
                assertNotNull(pt);
            }
        }
    }

    private List<SingleStatusUpdate> prepareTest(TestOcrRunEnum seq, String requestId) {
        OffsetDateTime now = OffsetDateTime.now();
        List<String> requiredDoc = new ArrayList<>(seq.getSentDocuments());
        AtomicInteger delay = new AtomicInteger(0);
        String productType = ProductType.AR.getValue();

        return seq.getStatusCodes().stream().map(code -> {
            PaperProgressStatusEvent ev;
            ev = createSimpleAnalogMail(requestId, now, delay, productType);
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
            if (!CollectionUtils.isEmpty(conf.getDeliveryFailureCauseList()) &&
                    !conf.getDeliveryFailureCauseList().contains(DeliveryFailureCauseEnum.SKIP_VALIDATION)) {
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
}
*/
