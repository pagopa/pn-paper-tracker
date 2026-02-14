package it.pagopa.pn.papertracker.it.OCR;

import com.sngular.apigenerator.asyncapi.business_model.model.event.Data;
import com.sngular.apigenerator.asyncapi.business_model.model.event.OcrDataResultPayload;
import it.pagopa.pn.papertracker.BaseTest;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.SingleStatusUpdate;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackerDryRunOutputsDAO;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsErrorsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.middleware.msclient.DataVaultClient;
import it.pagopa.pn.papertracker.middleware.msclient.PaperChannelClient;
import it.pagopa.pn.papertracker.middleware.msclient.SafeStorageClient;
import it.pagopa.pn.papertracker.middleware.queue.consumer.internal.ExternalChannelHandler;
import it.pagopa.pn.papertracker.middleware.queue.consumer.internal.OcrEventHandler;
import it.pagopa.pn.papertracker.middleware.queue.model.OcrEvent;
import it.pagopa.pn.papertracker.middleware.queue.producer.OcrMomProducer;
import it.pagopa.pn.papertracker.model.DeliveryFailureCauseEnum;
import it.pagopa.pn.papertracker.model.EventStatusCodeEnum;
import it.pagopa.pn.papertracker.model.OcrStatusEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
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

import static it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ErrorCause.OCR_DRY_RUN_MODE;
import static it.pagopa.pn.papertracker.service.handler_step.TestUtils.*;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@TestPropertySource(locations = "classpath:application.test-ocr-dry.properties")
public class OcrDryTestIT extends BaseTest.WithLocalStack {

    @Autowired
    private ExternalChannelHandler externalChannelHandler;
    @Autowired
    private PaperTrackingsDAO paperTrackingsDAO;
    @Autowired
    private PaperTrackingsErrorsDAO paperTrackingsErrorsDAO;
    @Autowired
    private PaperTrackerDryRunOutputsDAO paperTrackerDryRunOutputsDAO;
    @Autowired
    private OcrEventHandler ocrEventHandler;


    @MockitoBean
    private SafeStorageClient safeStorageClient;
    @MockitoBean
    private PaperChannelClient paperChannelClient;
    @MockitoBean
    private DataVaultClient dataVaultClient;
    @MockitoBean
    private OcrMomProducer ocrMomProducer;


    @ParameterizedTest
    @EnumSource(value = TestOcrDryEnum.class)
    void testSequence(TestOcrDryEnum seq) {
        //Arrange
        when(safeStorageClient.getSafeStoragePresignedUrl(any())).thenReturn(Mono.just("url"));
        String iun = UUID.randomUUID().toString();
        String requestId = "PREPARE_ANALOG_DOMICILE.IUN_" + iun + ".RECINDEX_0.ATTEMPT_0.PCRETRY_0";
        PaperTrackings paperTrackings = getPaperTrackings(requestId, it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType.AR);
        paperTrackings.getValidationConfig().setOcrEnabled(OcrStatusEnum.DRY);
        paperTrackings.getValidationFlow().setOcrRequests(new ArrayList<>());
        paperTrackingsDAO.putIfAbsent(paperTrackings).block();
        List<SingleStatusUpdate> eventsToSend = prepareTest(seq, requestId);

        String messageIdForRecag012 = "RECAG012ID";
        String messageIdForFinalEvent = "finalEventOCR";

        //Act
        eventsToSend.forEach(singleStatusUpdate -> {
            String messageId = UUID.randomUUID().toString();
            assertNotNull(singleStatusUpdate.getAnalogMail());
            if(singleStatusUpdate.getAnalogMail().getStatusCode().equalsIgnoreCase("RECAG012")){
                messageId = messageIdForRecag012;
            } else if (singleStatusUpdate.getAnalogMail().getStatusCode().endsWith("C") || singleStatusUpdate.getAnalogMail().getStatusCode().endsWith("F")) {
                messageId = messageIdForFinalEvent;
            }
            externalChannelHandler.handleExternalChannelMessage(singleStatusUpdate, true, null, messageId, null);
        });

        //Assert
        await().pollDelay(Duration.ofSeconds(1)).until(() -> true);

        ocrEventHandler.handleOcrMessage(OcrDataResultPayload.builder()
                .CommandId(paperTrackings.getTrackingId() + "#" + messageIdForFinalEvent + "#" + "23L")
                .data(Data.builder()
                        .description("ok")
                        .validationStatus(Data.ValidationStatus.OK)
                        .predictedRefinementType(Data.PredictedRefinementType.PRE10)
                        .validationType(Data.ValidationType.AI)
                        .build())
                .build());
        PaperTrackings pt = paperTrackingsDAO.retrieveEntityByTrackingId(requestId).block();
        List<PaperTrackingsErrors> errors = paperTrackingsErrorsDAO.retrieveErrors(requestId).collectList().block();
        List<PaperTrackerDryRunOutputs> outputs = paperTrackerDryRunOutputsDAO.retrieveOutputEvents(requestId).collectList().block();

        assertNotNull(pt);
        verifyPaperTrackings(pt, seq);
        verifyErrors(errors, seq);
        assertNotNull(outputs);
        verifyOutputs(outputs, seq);


    }
    private void verifyOutputs(List<PaperTrackerDryRunOutputs> outputs, TestOcrDryEnum seq) {
        switch (seq){
            case TEST_OCR_DRY_890 -> {
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
            case TEST_OCR_DRY_890_WITHOUT_RECAG012,
                 TEST_OCR_DRY_890_WITHOUT_RECAG012_NO_REQUIRED_ATTACHMENTS -> {
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
            case TEST_OCR_DRY_890_RECAG012_BEFORE_ATTACHMENTS -> {
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
            case TEST_OCR_DRY_890_RECAG012_AFTER_ATTACHMENTS -> {
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

    private void verifyErrors(List<PaperTrackingsErrors> errors, TestOcrDryEnum seq) {
        switch (seq){
            case TEST_OCR_DRY_890, TEST_OCR_DRY_890_RECAG012_AFTER_ATTACHMENTS,
                 TEST_OCR_DRY_890_RECAG012_BEFORE_ATTACHMENTS -> {
                assertNotNull(errors);
                Assertions.assertEquals(1, errors.size());
                PaperTrackingsErrors error = errors.getFirst();
                Assertions.assertEquals("INFO", error.getType().name());
                Assertions.assertEquals(ErrorCategory.OCR_VALIDATION, error.getErrorCategory());
                Assertions.assertEquals(OCR_DRY_RUN_MODE, error.getDetails().getCause());
            }
            case TEST_OCR_DRY_890_WITHOUT_RECAG012, TEST_OCR_DRY_890_WITHOUT_RECAG012_NO_REQUIRED_ATTACHMENTS -> {
                assertNotNull(errors);
                Assertions.assertEquals(2, errors.size());
                PaperTrackingsErrors error = errors.getFirst();
                Assertions.assertEquals("INCONSISTENT_STATE", error.getErrorCategory().name());
                Assertions.assertEquals("ERROR", error.getType().name());
                Assertions.assertEquals("SEQUENCE_VALIDATION", error.getFlowThrow().name());
                Assertions.assertEquals("invalid AWAITING_REFINEMENT state for stock 890", error.getDetails().getMessage());
                PaperTrackingsErrors error2 = errors.getLast();
                Assertions.assertEquals("INFO", error2.getType().name());
                Assertions.assertEquals(ErrorCategory.OCR_VALIDATION, error2.getErrorCategory());
                Assertions.assertEquals(OCR_DRY_RUN_MODE, error2.getDetails().getCause());

            }
        }
    }

    private void verifyPaperTrackings(PaperTrackings pt, TestOcrDryEnum seq) {
        switch (seq){
            case TEST_OCR_DRY_890 -> {
                assertNotNull(pt);
                Assertions.assertEquals("DONE", pt.getState().name());
                Assertions.assertEquals("DONE", pt.getBusinessState().name());
                verify(ocrMomProducer, times(1)).push(any(OcrEvent.class));
            }
            case TEST_OCR_DRY_890_RECAG012_AFTER_ATTACHMENTS, TEST_OCR_DRY_890_RECAG012_BEFORE_ATTACHMENTS -> {
                assertNotNull(pt);
                Assertions.assertEquals("DONE", pt.getState().name());
                Assertions.assertEquals("DONE", pt.getBusinessState().name());
                verify(ocrMomProducer, times(2)).push(any(OcrEvent.class));
            }
            case TEST_OCR_DRY_890_WITHOUT_RECAG012, TEST_OCR_DRY_890_WITHOUT_RECAG012_NO_REQUIRED_ATTACHMENTS -> {
                assertNotNull(pt);
                Assertions.assertEquals("AWAITING_REFINEMENT", pt.getState().name());
                Assertions.assertEquals("KO", pt.getBusinessState().name());
                verify(ocrMomProducer, times(0)).push(any(OcrEvent.class));
            }
        }
    }


    private List<SingleStatusUpdate> prepareTest(TestOcrDryEnum seq, String requestId) {
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
