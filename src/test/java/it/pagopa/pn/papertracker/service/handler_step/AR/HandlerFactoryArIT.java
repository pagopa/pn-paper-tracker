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
import it.pagopa.pn.papertracker.model.SequenceElement;
import lombok.Getter;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

import static it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsState.DONE;
import static it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsState.KO;
import static it.pagopa.pn.papertracker.service.handler_step.AR.TestSequenceEnum.FURTO_SMARRIMENTO_DETERIORAMENTO;
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
    void testCsvInput(TestSequenceEnum testSequenceEnum) {
        PcRetryResponse pcRetryResponse = null;
        List<String> statusCodes = testSequenceEnum.getStatusCodes();

        String iun = UUID.randomUUID().toString();
        String requestId = "PREPARE_ANALOG_DOMICILE.IUN_" + iun + ".RECINDEX_0.ATTEMPT_0.PCRETRY_0";
        paperTrackingsDAO.putIfAbsent(getPaperTrackings(requestId)).block();
        when(safeStorageClient.getSafeStoragePresignedUrl(any())).thenReturn(Mono.just("url"));
        if (statusCodes.contains("RECRN006")) {
            pcRetryResponse = new PcRetryResponse();
            if (statusCodes.size() == 1) {
                String newRequestId = "PREPARE_ANALOG_DOMICILE.IUN_" + iun + ".RECINDEX_0.ATTEMPT_0.PCRETRY_1";
                pcRetryResponse.setRetryFound(true);
                pcRetryResponse.setRequestId(newRequestId);
                pcRetryResponse.setParentRequestId(requestId);
                pcRetryResponse.setDeliveryDriverId("POSTE");
                pcRetryResponse.setPcRetry("PCRETRY_1");

            } else {
                pcRetryResponse.setRetryFound(false);
            }
            when(paperChannelClient.getPcRetry(any())).thenReturn(Mono.just(pcRetryResponse));
        }

        List<String> documentList = new ArrayList<>(testSequenceEnum.getSentDocuments());

        OffsetDateTime now = OffsetDateTime.now();
        statusCodes.forEach(statusCode -> {
            PaperProgressStatusEvent analogMail = createSimpleAnalogMail(requestId, now);
            if(statusCode.equalsIgnoreCase("RECRN005C") || statusCode.equalsIgnoreCase("RECRN005B") || statusCode.equalsIgnoreCase("RECRN005A")){
                analogMail.setClientRequestTimeStamp(analogMail.getClientRequestTimeStamp().plusDays(60));
                analogMail.setStatusDateTime(analogMail.getStatusDateTime().plusDays(60));
            }
            StatusCodeConfiguration.StatusCodeConfigurationEnum statusCodeConfiguration = StatusCodeConfiguration.StatusCodeConfigurationEnum.fromKey(statusCode);
            analogMail.setStatusCode(statusCode);
            analogMail.setStatusDescription(statusCodeConfiguration.getStatusCodeDescription());
            if (!CollectionUtils.isEmpty(statusCodeConfiguration.getDeliveryFailureCauseList())) {
                analogMail.setDeliveryFailureCause(statusCodeConfiguration.getDeliveryFailureCauseList().getFirst().name());
            }
            if (!CollectionUtils.isEmpty(documentList)) {
                analogMail.setAttachments(constructAttachments(statusCode, documentList));
            }
            SingleStatusUpdate extChannelMessage = new SingleStatusUpdate();
            extChannelMessage.setAnalogMail(analogMail);

            externalChannelHandler.handleExternalChannelMessage(extChannelMessage);
        });
        PaperTrackings newPaperTrackings = null;
        PaperTrackings paperTrackings = paperTrackingsDAO.retrieveEntityByRequestId(requestId).block();
        if (Objects.nonNull(pcRetryResponse) && StringUtils.hasText(pcRetryResponse.getRequestId())) {
            newPaperTrackings = paperTrackingsDAO.retrieveEntityByRequestId(pcRetryResponse.getRequestId()).block();
        }
        List<PaperTrackingsErrors> paperTrackingsErrors = paperTrackingsErrorsDAO.retrieveErrors(requestId).collectList().block();
        List<PaperTrackerDryRunOutputs> paperTrackerDryRunOutputs = paperTrackerDryRunOutputsDAO.retrieveOutputEvents(requestId).collectList().block();
        verifyResult(paperTrackings, newPaperTrackings, paperTrackingsErrors, paperTrackerDryRunOutputs, testSequenceEnum);
    }

    private void verifyResult(PaperTrackings paperTrackings, PaperTrackings newPaperTrackings, List<PaperTrackingsErrors> paperTrackingsErrors, List<PaperTrackerDryRunOutputs> paperTrackerDryRunOutputs, TestSequenceEnum testSequenceEnum) {
        verifyPaperTrackings(paperTrackings, testSequenceEnum);
        verifyPaperErrors(paperTrackingsErrors, testSequenceEnum);
        verifyOutput(paperTrackerDryRunOutputs, testSequenceEnum);
        verifyNewPaperTrackings(newPaperTrackings, paperTrackings, testSequenceEnum);
    }

    private void verifyNewPaperTrackings(PaperTrackings newPaperTrackings, PaperTrackings paperTrackings, TestSequenceEnum testSequenceEnum) {
        if(testSequenceEnum.equals(FURTO_SMARRIMENTO_DETERIORAMENTO)){
            Assertions.assertNotNull(newPaperTrackings);
            Assertions.assertEquals(paperTrackings.getProductType(), newPaperTrackings.getProductType());
            Assertions.assertEquals(PaperTrackingsState.AWAITING_FINAL_STATUS_CODE, newPaperTrackings.getState());
            Assertions.assertEquals(paperTrackings.getUnifiedDeliveryDriver(), newPaperTrackings.getUnifiedDeliveryDriver());
            Assertions.assertTrue(newPaperTrackings.getTrackingId().endsWith(".RECINDEX_0.ATTEMPT_0.PCRETRY_1"));
        }else{
            Assertions.assertNull(newPaperTrackings);
        }
    }

    private void verifyOutput(List<PaperTrackerDryRunOutputs> paperTrackerDryRunOutputs, TestSequenceEnum testSequenceEnum) {
        //TODO: CHECK OUTPUT
    }

    private void verifyPaperErrors(List<PaperTrackingsErrors> paperTrackingsErrors, TestSequenceEnum testSequenceEnum) {
        switch (testSequenceEnum) {
            case OK_CONSEGNATO_FASCICOLO_CHIUSO, MANCATA_CONSEGNA_FASCICOLO_CHIUSO,
                 IRREPERIBILITA_ASSOLUTA_FASCICOLO_CHIUSO, FURTO_SMARRIMENTO_DETERIORAMENTO,
                 CONSEGNATO_GIACENZA_FASCICOLO_CHIUSO, MANCATA_CONSEGNA_GIACENZA_FASCICOLO_CHIUSO,
                 OK_CONSEGNATO_FASCICOLO_CHIUSO_AB_DUPLICATI_OK, OK_CONSEGNATO_FASCICOLO_CHIUSO_010_DUPLICATO_OK,
                 OK_CONSEGNATO_FASCICOLO_CHIUSO_011_DUPLICATO_OK, CONSEGNATO_FASCICOLO_CHIUSO_STATO_NON_INERENTE,
                 IRREPERIBILITA_ASSOLUTA_FASCICOLO_CHIUSO_STATO_NON_INERENTE,
                 IRREPERIBILITA_ASSOLUTA_FASCICOLO_CHIUSO_ALLEGATI_SEPARATI,
                 COMPIUTA_GIACENZA_GIACENZA_FASCICOLO_CHIUSO-> Assertions.assertEquals(0, paperTrackingsErrors.size());

            case INESITO_FURTO_SMARRIMENTO_DETERIORAMENTO, INESITO_INGIACENZA_FURTO_SMARRIMENTO_DETERIORAMENTO -> {
                Assertions.assertEquals(1, paperTrackingsErrors.size());
                PaperTrackingsErrors paperTrackingsError = paperTrackingsErrors.getFirst();
                Assertions.assertEquals(ErrorCategory.MAX_RETRY_REACHED_ERROR, paperTrackingsError.getErrorCategory());
                Assertions.assertEquals(FlowThrow.RETRY_PHASE, paperTrackingsError.getFlowThrow());
                Assertions.assertEquals(ErrorType.ERROR, paperTrackingsError.getType());
                Assertions.assertNotNull(paperTrackingsError.getCreated());
                Assertions.assertNotNull(paperTrackingsError.getTrackingId());
                Assertions.assertNotNull(paperTrackingsError.getProductType());
                Assertions.assertEquals("Retry not found for trackingId: " + paperTrackingsError.getTrackingId(), paperTrackingsError.getDetails().getMessage());
                Assertions.assertNull(paperTrackingsError.getDetails().getCause());
            }
            case CONSEGNATO_GIACENZA_FASCICOLO_CHIUSO_ALLEGATI_MANCANTI,
                 IRREPERIBILITA_ASSOLUTA_FASCICOLO_CHIUSO_ALLEGATI_MANCANTI -> {
                Assertions.assertEquals(1, paperTrackingsErrors.size());
                PaperTrackingsErrors paperTrackingsError = paperTrackingsErrors.getFirst();
                Assertions.assertEquals(ErrorCategory.ATTACHMENTS_ERROR, paperTrackingsError.getErrorCategory());
                Assertions.assertEquals(FlowThrow.SEQUENCE_VALIDATION, paperTrackingsError.getFlowThrow());
                Assertions.assertEquals(ErrorType.ERROR, paperTrackingsError.getType());
                Assertions.assertNotNull(paperTrackingsError.getCreated());
                Assertions.assertNotNull(paperTrackingsError.getTrackingId());
                Assertions.assertNotNull(paperTrackingsError.getProductType());
                Assertions.assertTrue(paperTrackingsError.getDetails().getMessage().contains("Attachments are not valid for the sequence element: "));
                Assertions.assertNull(paperTrackingsError.getDetails().getCause());
            }
            case CONSEGNATO_FASCICOLO_CHIUSO_STATO_ASSENTE -> {
                Assertions.assertEquals(1, paperTrackingsErrors.size());
                PaperTrackingsErrors paperTrackingsError = paperTrackingsErrors.getFirst();
                Assertions.assertEquals(ErrorCategory.STATUS_CODE_ERROR, paperTrackingsError.getErrorCategory());
                Assertions.assertEquals(FlowThrow.SEQUENCE_VALIDATION, paperTrackingsError.getFlowThrow());
                Assertions.assertEquals(ErrorType.ERROR, paperTrackingsError.getType());
                Assertions.assertNotNull(paperTrackingsError.getCreated());
                Assertions.assertNotNull(paperTrackingsError.getTrackingId());
                Assertions.assertNotNull(paperTrackingsError.getProductType());
                Assertions.assertEquals("Necessary status code not found in events", paperTrackingsError.getDetails().getMessage());
                Assertions.assertNull(paperTrackingsError.getDetails().getCause());
            }
        }
    }


    private void verifyPaperTrackings(PaperTrackings paperTrackings, TestSequenceEnum testSequenceEnum) {
        Assertions.assertNull(paperTrackings.getOcrRequestId());
        Assertions.assertTrue(paperTrackings.getEvents().stream().map(Event::getStatusCode).toList().containsAll(testSequenceEnum.getStatusCodes()));

        switch (testSequenceEnum) {
            case OK_CONSEGNATO_FASCICOLO_CHIUSO -> {
                Assertions.assertEquals(DONE, paperTrackings.getState());
                Assertions.assertEquals(3, paperTrackings.getEvents().size());
                Assertions.assertEquals(3, paperTrackings.getPaperStatus().getValidatedEvents().size());
                Assertions.assertTrue(paperTrackings.getPaperStatus().getValidatedEvents().stream().map(Event::getStatusCode).toList().containsAll(testSequenceEnum.getStatusCodes()));
                Assertions.assertNull(paperTrackings.getNextRequestIdPcretry());
                Assertions.assertNull(paperTrackings.getPaperStatus().getDeliveryFailureCause());
                Assertions.assertFalse(paperTrackings.getValidationFlow().getOcrEnabled());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getSequencesValidationTimestamp());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getDematValidationTimestamp());
                Assertions.assertNull(paperTrackings.getValidationFlow().getOcrRequestTimestamp());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getRegisteredLetterCode());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getFinalStatusCode());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getValidatedSequenceTimestamp());
            }
            case MANCATA_CONSEGNA_FASCICOLO_CHIUSO -> {
                Assertions.assertEquals(DONE, paperTrackings.getState());
                Assertions.assertEquals(3, paperTrackings.getEvents().size());
                Assertions.assertEquals(3, paperTrackings.getPaperStatus().getValidatedEvents().size());
                Assertions.assertTrue(paperTrackings.getPaperStatus().getValidatedEvents().stream().map(Event::getStatusCode).toList().containsAll(testSequenceEnum.getStatusCodes()));
                Assertions.assertNull(paperTrackings.getNextRequestIdPcretry());
                Assertions.assertEquals("M02", paperTrackings.getPaperStatus().getDeliveryFailureCause());
                Assertions.assertFalse(paperTrackings.getValidationFlow().getOcrEnabled());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getSequencesValidationTimestamp());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getDematValidationTimestamp());
                Assertions.assertNull(paperTrackings.getValidationFlow().getOcrRequestTimestamp());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getRegisteredLetterCode());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getFinalStatusCode());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getValidatedSequenceTimestamp());
            }
            case IRREPERIBILITA_ASSOLUTA_FASCICOLO_CHIUSO -> {
                Assertions.assertEquals(DONE, paperTrackings.getState());
                Assertions.assertEquals(3, paperTrackings.getEvents().size());
                Assertions.assertEquals(3, paperTrackings.getPaperStatus().getValidatedEvents().size());
                Assertions.assertTrue(paperTrackings.getPaperStatus().getValidatedEvents().stream().map(Event::getStatusCode).toList().containsAll(testSequenceEnum.getStatusCodes()));
                Assertions.assertNull(paperTrackings.getNextRequestIdPcretry());
                Assertions.assertEquals("M01", paperTrackings.getPaperStatus().getDeliveryFailureCause());
                Assertions.assertFalse(paperTrackings.getValidationFlow().getOcrEnabled());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getSequencesValidationTimestamp());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getDematValidationTimestamp());
                Assertions.assertNull(paperTrackings.getValidationFlow().getOcrRequestTimestamp());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getRegisteredLetterCode());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getFinalStatusCode());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getValidatedSequenceTimestamp());
            }
            case FURTO_SMARRIMENTO_DETERIORAMENTO -> {
                Assertions.assertEquals(DONE, paperTrackings.getState());
                Assertions.assertEquals(1, paperTrackings.getEvents().size());
                Assertions.assertNotNull(paperTrackings.getNextRequestIdPcretry());
            }
            case INESITO_FURTO_SMARRIMENTO_DETERIORAMENTO -> {
                Assertions.assertEquals(DONE, paperTrackings.getState());
                Assertions.assertEquals(2, paperTrackings.getEvents().size());
                Assertions.assertNull(paperTrackings.getNextRequestIdPcretry());
            }
            case INESITO_INGIACENZA_FURTO_SMARRIMENTO_DETERIORAMENTO -> {
                Assertions.assertEquals(DONE, paperTrackings.getState());
                Assertions.assertEquals(3, paperTrackings.getEvents().size());
                Assertions.assertNull(paperTrackings.getNextRequestIdPcretry());
            }
            case CONSEGNATO_GIACENZA_FASCICOLO_CHIUSO_ALLEGATI_MANCANTI, CONSEGNATO_FASCICOLO_CHIUSO_STATO_ASSENTE -> {
                Assertions.assertEquals(KO, paperTrackings.getState());
                Assertions.assertEquals(5, paperTrackings.getEvents().size());
                Assertions.assertNull(paperTrackings.getNextRequestIdPcretry());
            }
            case CONSEGNATO_GIACENZA_FASCICOLO_CHIUSO, COMPIUTA_GIACENZA_GIACENZA_FASCICOLO_CHIUSO,
                 MANCATA_CONSEGNA_GIACENZA_FASCICOLO_CHIUSO -> {
                Assertions.assertEquals(DONE, paperTrackings.getState());
                Assertions.assertEquals(5, paperTrackings.getEvents().size());
                Assertions.assertEquals(5, paperTrackings.getPaperStatus().getValidatedEvents().size());
                Assertions.assertTrue(paperTrackings.getPaperStatus().getValidatedEvents().stream().map(Event::getStatusCode).toList().containsAll(testSequenceEnum.getStatusCodes()));
                Assertions.assertNull(paperTrackings.getNextRequestIdPcretry());
                Assertions.assertNull(paperTrackings.getPaperStatus().getDeliveryFailureCause());
                Assertions.assertFalse(paperTrackings.getValidationFlow().getOcrEnabled());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getSequencesValidationTimestamp());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getDematValidationTimestamp());
                Assertions.assertNull(paperTrackings.getValidationFlow().getOcrRequestTimestamp());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getRegisteredLetterCode());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getFinalStatusCode());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getValidatedSequenceTimestamp());
            }
            case OK_CONSEGNATO_FASCICOLO_CHIUSO_AB_DUPLICATI_OK -> {
                Assertions.assertEquals(DONE, paperTrackings.getState());
                Assertions.assertEquals(6, paperTrackings.getEvents().size());
                Assertions.assertEquals(3, paperTrackings.getPaperStatus().getValidatedEvents().size());
                Assertions.assertTrue(paperTrackings.getPaperStatus().getValidatedEvents().stream().map(Event::getStatusCode).toList().containsAll(List.of("RECRN001A", "RECRN001B", "RECRN001C")));
                Assertions.assertNull(paperTrackings.getNextRequestIdPcretry());
                Assertions.assertNull(paperTrackings.getPaperStatus().getDeliveryFailureCause());
                Assertions.assertFalse(paperTrackings.getValidationFlow().getOcrEnabled());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getSequencesValidationTimestamp());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getDematValidationTimestamp());
                Assertions.assertNull(paperTrackings.getValidationFlow().getOcrRequestTimestamp());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getRegisteredLetterCode());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getFinalStatusCode());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getValidatedSequenceTimestamp());
            }
            case OK_CONSEGNATO_FASCICOLO_CHIUSO_010_DUPLICATO_OK, OK_CONSEGNATO_FASCICOLO_CHIUSO_011_DUPLICATO_OK -> {
                Assertions.assertEquals(DONE, paperTrackings.getState());
                Assertions.assertEquals(6, paperTrackings.getEvents().size());
                Assertions.assertEquals(5, paperTrackings.getPaperStatus().getValidatedEvents().size());
                Assertions.assertTrue(paperTrackings.getPaperStatus().getValidatedEvents().stream().map(Event::getStatusCode).toList().containsAll(List.of("RECRN010", "RECRN011", "RECRN003A", "RECRN003B", "RECRN003C")));
                Assertions.assertNull(paperTrackings.getNextRequestIdPcretry());
                Assertions.assertNull(paperTrackings.getPaperStatus().getDeliveryFailureCause());
                Assertions.assertFalse(paperTrackings.getValidationFlow().getOcrEnabled());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getSequencesValidationTimestamp());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getDematValidationTimestamp());
                Assertions.assertNull(paperTrackings.getValidationFlow().getOcrRequestTimestamp());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getRegisteredLetterCode());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getFinalStatusCode());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getValidatedSequenceTimestamp());
            }
            case CONSEGNATO_FASCICOLO_CHIUSO_STATO_NON_INERENTE -> {
                Assertions.assertEquals(DONE, paperTrackings.getState());
                Assertions.assertEquals(4, paperTrackings.getEvents().size());
                Assertions.assertEquals(3, paperTrackings.getPaperStatus().getValidatedEvents().size());
                Assertions.assertTrue(paperTrackings.getPaperStatus().getValidatedEvents().stream().map(Event::getStatusCode).toList().containsAll(List.of("RECRN001A", "RECRN001B", "RECRN001C")));
                Assertions.assertNull(paperTrackings.getNextRequestIdPcretry());
                Assertions.assertFalse(paperTrackings.getValidationFlow().getOcrEnabled());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getSequencesValidationTimestamp());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getDematValidationTimestamp());
                Assertions.assertNull(paperTrackings.getValidationFlow().getOcrRequestTimestamp());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getRegisteredLetterCode());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getFinalStatusCode());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getValidatedSequenceTimestamp());
            }
            case IRREPERIBILITA_ASSOLUTA_FASCICOLO_CHIUSO_STATO_NON_INERENTE -> {
                Assertions.assertEquals(DONE, paperTrackings.getState());
                Assertions.assertEquals(7, paperTrackings.getEvents().size());
                Assertions.assertEquals(5, paperTrackings.getPaperStatus().getValidatedEvents().size());
                Assertions.assertTrue(paperTrackings.getPaperStatus().getValidatedEvents().stream().map(Event::getStatusCode).toList().containsAll(List.of("RECRN010", "RECRN011", "RECRN004A", "RECRN004B", "RECRN004C")));
                Assertions.assertNull(paperTrackings.getNextRequestIdPcretry());
                Assertions.assertNull(paperTrackings.getPaperStatus().getDeliveryFailureCause());
                Assertions.assertFalse(paperTrackings.getValidationFlow().getOcrEnabled());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getSequencesValidationTimestamp());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getDematValidationTimestamp());
                Assertions.assertNull(paperTrackings.getValidationFlow().getOcrRequestTimestamp());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getRegisteredLetterCode());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getFinalStatusCode());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getValidatedSequenceTimestamp());
            }
            case IRREPERIBILITA_ASSOLUTA_FASCICOLO_CHIUSO_ALLEGATI_SEPARATI -> {
                Assertions.assertEquals(DONE, paperTrackings.getState());
                Assertions.assertEquals(4, paperTrackings.getEvents().size());
                Assertions.assertEquals(4, paperTrackings.getPaperStatus().getValidatedEvents().size());
                Assertions.assertTrue(paperTrackings.getPaperStatus().getValidatedEvents().stream().map(Event::getStatusCode).toList().containsAll(testSequenceEnum.getStatusCodes()));
                Assertions.assertNull(paperTrackings.getNextRequestIdPcretry());
                Assertions.assertEquals("M01", paperTrackings.getPaperStatus().getDeliveryFailureCause());
                Assertions.assertFalse(paperTrackings.getValidationFlow().getOcrEnabled());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getSequencesValidationTimestamp());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getDematValidationTimestamp());
                Assertions.assertNull(paperTrackings.getValidationFlow().getOcrRequestTimestamp());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getRegisteredLetterCode());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getFinalStatusCode());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getValidatedSequenceTimestamp());
            }
            case IRREPERIBILITA_ASSOLUTA_FASCICOLO_CHIUSO_ALLEGATI_MANCANTI -> {
                Assertions.assertEquals(KO, paperTrackings.getState());
                Assertions.assertEquals(3, paperTrackings.getEvents().size());
                Assertions.assertNull(paperTrackings.getNextRequestIdPcretry());
            }
        }
    }

    private PaperTrackings getPaperTrackings(String requestId) {
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setTrackingId(requestId);
        paperTrackings.setProductType(ProductType.AR);
        paperTrackings.setUnifiedDeliveryDriver("POSTE");
        paperTrackings.setState(PaperTrackingsState.AWAITING_FINAL_STATUS_CODE);
        paperTrackings.setCreatedAt(Instant.now());
        paperTrackings.setValidationFlow(new ValidationFlow());
        paperTrackings.setPaperStatus(new PaperStatus());
        return paperTrackings;
    }

    private List<AttachmentDetails> constructAttachments(String statusCode, List<String> documentList) {
        List<String> documentTypeList = documentList.stream()
                .filter(s -> s.split("-")[0].equalsIgnoreCase(statusCode))
                .toList();

        if (!CollectionUtils.isEmpty(documentTypeList)) {
            List<AttachmentDetails> attachmentDetails = getAttachmentDetails(documentTypeList.getFirst());
            documentList.remove(documentTypeList.getFirst());
            return attachmentDetails;
        }
        return Collections.emptyList();
    }

    private List<AttachmentDetails> getAttachmentDetails(String first) {
        List<String> types = Arrays.stream(first.split("-")[1].split("#")).toList();
        return types.stream().map(type -> AttachmentDetails.builder()
                        .documentType(type)
                        .sha256("sha256")
                        .id("id-" + first)
                        .uri("https://example.com/" + first)
                        .date(OffsetDateTime.now())
                        .build())
                .toList();
    }

    public static PaperProgressStatusEvent createSimpleAnalogMail(String requestId, OffsetDateTime now) {
        PaperProgressStatusEvent analogMail = new PaperProgressStatusEvent();
        analogMail.requestId(requestId);
        analogMail.setClientRequestTimeStamp(OffsetDateTime.now());
        analogMail.setStatusDateTime(now);
        analogMail.setIun("MUMR-VQMP-LDNZ-202303-H-1");
        analogMail.setProductType("AR");
        analogMail.setRegisteredLetterCode("registeredLetterCode");
        return analogMail;
    }

}
