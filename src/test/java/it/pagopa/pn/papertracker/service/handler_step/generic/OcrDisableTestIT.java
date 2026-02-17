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
import it.pagopa.pn.papertracker.middleware.queue.model.OcrEvent;
import it.pagopa.pn.papertracker.middleware.queue.producer.OcrMomProducer;
import it.pagopa.pn.papertracker.model.DeliveryFailureCauseEnum;
import it.pagopa.pn.papertracker.model.EventStatusCodeEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.test.context.TestPropertySource;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@TestPropertySource(locations = "classpath:application.test-ocr-disable.properties")
public class OcrDisableTestIT  extends BaseTest.WithLocalStack {

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
    @MockitoBean
    private OcrMomProducer ocrMomProducer;

    @ParameterizedTest
    @EnumSource(value = TestOcrDisableEnum.class)
    void testSequence(TestOcrDisableEnum seq){
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
        verifyNoInteractions(safeStorageClient);
        verify(ocrMomProducer, times(0)).push(any(OcrEvent.class));
    }

    private void verifyOutputs(List<PaperTrackerDryRunOutputs> outputs, TestOcrDisableEnum seq) {
        switch (seq){
                case TEST_OCR_DISABLE_890 -> {
                    assertNotNull(outputs);
                    List<PaperTrackerDryRunOutputs> sorted = outputs.stream()
                            .sorted((o1, o2) -> o1.getCreated().compareTo(o2.getCreated()))
                            .toList();
                    Assertions.assertEquals("CON080", sorted.get(0).getStatusDetail());
                    Assertions.assertEquals("CON020", sorted.get(1).getStatusDetail());
                    Assertions.assertEquals("RECAG003A", sorted.get(2).getStatusDetail());
                    Assertions.assertEquals("RECAG003B", sorted.get(3).getStatusDetail());
                    Assertions.assertEquals("RECAG003C", sorted.get(4).getStatusDetail());
                }
                case TEST_OCR_DISABLE_890_WITHOUT_RECAG012,
                     TEST_OCR_DISABLE_890_WITHOUT_RECAG012_NO_REQUIRED_ATTACHMENTS -> {
                    assertNotNull(outputs);
                    List<PaperTrackerDryRunOutputs> sorted = outputs.stream()
                            .sorted((o1, o2) -> o1.getCreated().compareTo(o2.getCreated()))
                            .toList();
                    Assertions.assertEquals("RECAG010", sorted.get(0).getStatusDetail());
                    Assertions.assertEquals("RECAG011A", sorted.get(1).getStatusDetail());
                    Assertions.assertEquals("RECAG011B", sorted.get(2).getStatusDetail());
                    Assertions.assertEquals("RECAG011B", sorted.get(3).getStatusDetail());
                    Assertions.assertEquals("RECAG008A", sorted.get(4).getStatusDetail());
                    Assertions.assertEquals("RECAG008B", sorted.get(5).getStatusDetail());
                }
            case TEST_OCR_DISABLE_890_RECAG012_BEFORE_ATTACHMENTS -> {
                    assertNotNull(outputs);
                    List<PaperTrackerDryRunOutputs> sorted = outputs.stream()
                            .sorted((o1, o2) -> o1.getCreated().compareTo(o2.getCreated()))
                            .toList();
                    Assertions.assertEquals("RECAG010", sorted.get(0).getStatusDetail());
                    Assertions.assertEquals("RECAG011A", sorted.get(1).getStatusDetail());
                    Assertions.assertEquals("RECAG012A", sorted.get(2).getStatusDetail());
                    Assertions.assertEquals("RECAG011B", sorted.get(3).getStatusDetail());
                    Assertions.assertEquals("RECAG012", sorted.get(4).getStatusDetail());
                    Assertions.assertEquals("RECAG007A", sorted.get(5).getStatusDetail());
                    Assertions.assertEquals("RECAG007B", sorted.get(6).getStatusDetail());
                    Assertions.assertEquals("RECAG007B", sorted.get(7).getStatusDetail());
                    Assertions.assertEquals("RECAG007C", sorted.get(8).getStatusDetail());
                }
                case TEST_OCR_DISABLE_890_RECAG012_AFTER_ATTACHMENTS -> {
                    assertNotNull(outputs);
                    List<PaperTrackerDryRunOutputs> sorted = outputs.stream()
                            .sorted((o1, o2) -> o1.getCreated().compareTo(o2.getCreated()))
                            .toList();
                    Assertions.assertEquals("RECAG010", sorted.get(0).getStatusDetail());
                    Assertions.assertEquals("RECAG011A", sorted.get(1).getStatusDetail());
                    Assertions.assertEquals("RECAG011B", sorted.get(2).getStatusDetail());
                    Assertions.assertEquals("RECAG012", sorted.get(3).getStatusDetail());
                    Assertions.assertEquals("RECAG011B", sorted.get(4).getStatusDetail());
                    Assertions.assertEquals("RECAG008A", sorted.get(5).getStatusDetail());
                    Assertions.assertEquals("RECAG008B", sorted.get(6).getStatusDetail());
                    Assertions.assertEquals("RECAG008C", sorted.get(7).getStatusDetail());
                }
        }
    }

    private void verifyErrors(List<PaperTrackingsErrors> errors, TestOcrDisableEnum seq) {
        switch (seq){
            case TEST_OCR_DISABLE_890, TEST_OCR_DISABLE_890_RECAG012_AFTER_ATTACHMENTS,
                 TEST_OCR_DISABLE_890_RECAG012_BEFORE_ATTACHMENTS -> {
                assertNotNull(errors);
                Assertions.assertEquals(0, errors.size());
            }
            case TEST_OCR_DISABLE_890_WITHOUT_RECAG012, TEST_OCR_DISABLE_890_WITHOUT_RECAG012_NO_REQUIRED_ATTACHMENTS -> {
                assertNotNull(errors);
                Assertions.assertEquals(1, errors.size());
                PaperTrackingsErrors error = errors.getFirst();
                Assertions.assertEquals("INCONSISTENT_STATE", error.getErrorCategory().name());
                Assertions.assertEquals("ERROR", error.getType().name());
                Assertions.assertEquals("SEQUENCE_VALIDATION", error.getFlowThrow().name());
                Assertions.assertEquals("invalid AWAITING_REFINEMENT state for stock 890", error.getDetails().getMessage());

            }
        }
    }

    private void verifyPaperTrackings(PaperTrackings pt, TestOcrDisableEnum seq) {
        switch (seq){
            case TEST_OCR_DISABLE_890, TEST_OCR_DISABLE_890_RECAG012_BEFORE_ATTACHMENTS,
                 TEST_OCR_DISABLE_890_RECAG012_AFTER_ATTACHMENTS -> {
                assertNotNull(pt);
                Assertions.assertEquals("DONE", pt.getState().name());
                Assertions.assertEquals("DONE", pt.getBusinessState().name());
            }
            case TEST_OCR_DISABLE_890_WITHOUT_RECAG012, TEST_OCR_DISABLE_890_WITHOUT_RECAG012_NO_REQUIRED_ATTACHMENTS -> {
                assertNotNull(pt);
                Assertions.assertEquals("AWAITING_REFINEMENT", pt.getState().name());
                Assertions.assertEquals("KO", pt.getBusinessState().name());
            }
        }
    }

    private List<SingleStatusUpdate> prepareTest(TestOcrDisableEnum seq, String requestId) {
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
