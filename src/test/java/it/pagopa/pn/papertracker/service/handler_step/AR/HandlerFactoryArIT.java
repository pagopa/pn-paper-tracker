package it.pagopa.pn.papertracker.service.handler_step.AR;

import it.pagopa.pn.papertracker.BaseTest;
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
import org.junit.jupiter.api.Assertions;
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

import static it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsState.DONE;
import static it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsState.KO;
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
    }

    private void verifyOutput(List<PaperTrackerDryRunOutputs> paperTrackerDryRunOutputs, TestSequenceEnum testSequenceEnum) {
    }

    private void verifyPaperErrors(List<PaperTrackingsErrors> paperTrackingsErrors, TestSequenceEnum testSequenceEnum) {
        switch (testSequenceEnum) {
            case OK_CONSEGNATO_FASCICOLO_CHIUSO -> {
                Assertions.assertEquals(0, paperTrackingsErrors.size());
            }
            case MANCATA_CONSEGNA_FASCICOLO_CHIUSO -> {
                Assertions.assertEquals(0, paperTrackingsErrors.size());
            }
            case IRREPERIBILITA_ASSOLUTA_FASCICOLO_CHIUSO -> {
                Assertions.assertEquals(0, paperTrackingsErrors.size());
            }
            case FURTO_SMARRIMENTO_DETERIORAMENTO -> {
                Assertions.assertEquals(0, paperTrackingsErrors.size());
            }
            case INESITO_FURTO_SMARRIMENTO_DETERIORAMENTO -> {
                Assertions.assertEquals(1, paperTrackingsErrors.size());
            }
            case INESITO_INGIACENZA_FURTO_SMARRIMENTO_DETERIORAMENTO -> {
                Assertions.assertEquals(1, paperTrackingsErrors.size());
            }
            case CONSEGNATO_GIACENZA_FASCICOLO_CHIUSO_ALLEGATI_MANCANTI -> {
                Assertions.assertEquals(1, paperTrackingsErrors.size());
            }
            case CONSEGNATO_GIACENZA_FASCICOLO_CHIUSO -> {
                Assertions.assertEquals(0, paperTrackingsErrors.size());
            }
            case MANCATA_CONSEGNA_GIACENZA_FASCICOLO_CHIUSO -> {
                Assertions.assertEquals(0, paperTrackingsErrors.size());
            }
            case COMPIUTA_GIACENZA_GIACENZA_FASCICOLO_CHIUSO -> {
                Assertions.assertEquals(1, paperTrackingsErrors.size());
            }
            case OK_CONSEGNATO_FASCICOLO_CHIUSO_AB_DUPLICATI_OK -> {
                Assertions.assertEquals(0, paperTrackingsErrors.size());
            }
            case OK_CONSEGNATO_FASCICOLO_CHIUSO_010_DUPLICATO_OK -> {
                Assertions.assertEquals(0, paperTrackingsErrors.size());
            }
            case OK_CONSEGNATO_FASCICOLO_CHIUSO_011_DUPLICATO_OK -> {
                Assertions.assertEquals(0, paperTrackingsErrors.size());
            }
            case CONSEGNATO_FASCICOLO_CHIUSO_STATO_ASSENTE -> {
                Assertions.assertEquals(1, paperTrackingsErrors.size());
            }
            case CONSEGNATO_FASCICOLO_CHIUSO_STATO_NON_INERENTE -> {
                Assertions.assertEquals(0, paperTrackingsErrors.size());
            }
            case IRREPERIBILITA_ASSOLUTA_FASCICOLO_CHIUSO_STATO_NON_INERENTE -> {
                Assertions.assertEquals(0, paperTrackingsErrors.size());
            }
            case IRREPERIBILITA_ASSOLUTA_FASCICOLO_CHIUSO_ALLEGATI_SEPARATI -> {
                Assertions.assertEquals(0, paperTrackingsErrors.size());
            }
            case IRREPERIBILITA_ASSOLUTA_FASCICOLO_CHIUSO_ALLEGATI_MANCANTI -> {
                Assertions.assertEquals(1, paperTrackingsErrors.size());
            }

        }
    }


    private void verifyPaperTrackings(PaperTrackings paperTrackings, TestSequenceEnum testSequenceEnum) {
        Assertions.assertNull(paperTrackings.getOcrRequestId());

        switch (testSequenceEnum) {
            case OK_CONSEGNATO_FASCICOLO_CHIUSO -> {
                Assertions.assertEquals(DONE, paperTrackings.getState());
                Assertions.assertEquals(3, paperTrackings.getEvents().size());
                Assertions.assertEquals(3, paperTrackings.getPaperStatus().getValidatedEvents().size());
                Assertions.assertNull(paperTrackings.getNextRequestIdPcretry());
                Assertions.assertNull(paperTrackings.getPaperStatus().getDeliveryFailureCause());
                Assertions.assertFalse(paperTrackings.getValidationFlow().getOcrEnabled());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getSequencesValidationTimestamp());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getDematValidationTimestamp());
                Assertions.assertNull(paperTrackings.getValidationFlow().getOcrRequestTimestamp());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getRegisteredLetterCode());
                // Assertions.assertNotNull(paperTrackings.getPaperStatus().getFinalStatusCode());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getValidatedSequenceTimestamp());
            }
            case MANCATA_CONSEGNA_FASCICOLO_CHIUSO -> {
                Assertions.assertEquals(DONE, paperTrackings.getState());
                Assertions.assertEquals(3, paperTrackings.getEvents().size());
                Assertions.assertEquals(3, paperTrackings.getPaperStatus().getValidatedEvents().size());
                Assertions.assertNull(paperTrackings.getNextRequestIdPcretry());
                Assertions.assertNull(paperTrackings.getPaperStatus().getDeliveryFailureCause());
                Assertions.assertFalse(paperTrackings.getValidationFlow().getOcrEnabled());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getSequencesValidationTimestamp());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getDematValidationTimestamp());
                Assertions.assertNull(paperTrackings.getValidationFlow().getOcrRequestTimestamp());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getRegisteredLetterCode());
                // Assertions.assertNotNull(paperTrackings.getPaperStatus().getFinalStatusCode());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getValidatedSequenceTimestamp());
            }
            case IRREPERIBILITA_ASSOLUTA_FASCICOLO_CHIUSO -> {
                Assertions.assertEquals(DONE, paperTrackings.getState());
                Assertions.assertEquals(3, paperTrackings.getEvents().size());
                Assertions.assertEquals(3, paperTrackings.getPaperStatus().getValidatedEvents().size());
                Assertions.assertNull(paperTrackings.getNextRequestIdPcretry());
                // Assertions.assertNotNull(paperTrackings.getPaperStatus().getDeliveryFailureCause());
                Assertions.assertFalse(paperTrackings.getValidationFlow().getOcrEnabled());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getSequencesValidationTimestamp());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getDematValidationTimestamp());
                Assertions.assertNull(paperTrackings.getValidationFlow().getOcrRequestTimestamp());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getRegisteredLetterCode());
                // Assertions.assertNotNull(paperTrackings.getPaperStatus().getFinalStatusCode());
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
            case CONSEGNATO_GIACENZA_FASCICOLO_CHIUSO_ALLEGATI_MANCANTI -> {
                Assertions.assertEquals(KO, paperTrackings.getState());
                Assertions.assertEquals(5, paperTrackings.getEvents().size());
                Assertions.assertNull(paperTrackings.getNextRequestIdPcretry());
            }
            case CONSEGNATO_GIACENZA_FASCICOLO_CHIUSO -> {
                Assertions.assertEquals(DONE, paperTrackings.getState());
                Assertions.assertEquals(5, paperTrackings.getEvents().size());
                Assertions.assertEquals(5, paperTrackings.getPaperStatus().getValidatedEvents().size());
                Assertions.assertNull(paperTrackings.getNextRequestIdPcretry());
                Assertions.assertNull(paperTrackings.getPaperStatus().getDeliveryFailureCause());
                Assertions.assertFalse(paperTrackings.getValidationFlow().getOcrEnabled());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getSequencesValidationTimestamp());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getDematValidationTimestamp());
                Assertions.assertNull(paperTrackings.getValidationFlow().getOcrRequestTimestamp());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getRegisteredLetterCode());
                // Assertions.assertNotNull(paperTrackings.getPaperStatus().getFinalStatusCode());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getValidatedSequenceTimestamp());
            }
            case MANCATA_CONSEGNA_GIACENZA_FASCICOLO_CHIUSO -> {
                Assertions.assertEquals(DONE, paperTrackings.getState());
                Assertions.assertEquals(5, paperTrackings.getEvents().size());
                Assertions.assertEquals(5, paperTrackings.getPaperStatus().getValidatedEvents().size());
                Assertions.assertNull(paperTrackings.getNextRequestIdPcretry());
                Assertions.assertNull(paperTrackings.getPaperStatus().getDeliveryFailureCause());
                Assertions.assertFalse(paperTrackings.getValidationFlow().getOcrEnabled());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getSequencesValidationTimestamp());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getDematValidationTimestamp());
                Assertions.assertNull(paperTrackings.getValidationFlow().getOcrRequestTimestamp());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getRegisteredLetterCode());
                // Assertions.assertNotNull(paperTrackings.getPaperStatus().getFinalStatusCode());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getValidatedSequenceTimestamp());
            }
            case COMPIUTA_GIACENZA_GIACENZA_FASCICOLO_CHIUSO -> {
                Assertions.assertEquals(KO, paperTrackings.getState());
                Assertions.assertEquals(5, paperTrackings.getEvents().size());
                Assertions.assertEquals(5, paperTrackings.getPaperStatus().getValidatedEvents().size());
                Assertions.assertNull(paperTrackings.getNextRequestIdPcretry());
                Assertions.assertNull(paperTrackings.getPaperStatus().getDeliveryFailureCause());
                Assertions.assertFalse(paperTrackings.getValidationFlow().getOcrEnabled());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getSequencesValidationTimestamp());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getDematValidationTimestamp());
                Assertions.assertNull(paperTrackings.getValidationFlow().getOcrRequestTimestamp());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getRegisteredLetterCode());
                // Assertions.assertNotNull(paperTrackings.getPaperStatus().getFinalStatusCode());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getValidatedSequenceTimestamp());
            }
            case OK_CONSEGNATO_FASCICOLO_CHIUSO_AB_DUPLICATI_OK -> {
                Assertions.assertEquals(DONE, paperTrackings.getState());
                Assertions.assertEquals(6, paperTrackings.getEvents().size());
                Assertions.assertEquals(3, paperTrackings.getPaperStatus().getValidatedEvents().size());
                Assertions.assertNull(paperTrackings.getNextRequestIdPcretry());
                Assertions.assertNull(paperTrackings.getPaperStatus().getDeliveryFailureCause());
                Assertions.assertFalse(paperTrackings.getValidationFlow().getOcrEnabled());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getSequencesValidationTimestamp());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getDematValidationTimestamp());
                Assertions.assertNull(paperTrackings.getValidationFlow().getOcrRequestTimestamp());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getRegisteredLetterCode());
                // Assertions.assertNotNull(paperTrackings.getPaperStatus().getFinalStatusCode());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getValidatedSequenceTimestamp());
            }
            case OK_CONSEGNATO_FASCICOLO_CHIUSO_010_DUPLICATO_OK -> {
                Assertions.assertEquals(DONE, paperTrackings.getState());
                Assertions.assertEquals(6, paperTrackings.getEvents().size());
                Assertions.assertEquals(5, paperTrackings.getPaperStatus().getValidatedEvents().size());
                Assertions.assertNull(paperTrackings.getNextRequestIdPcretry());
                Assertions.assertNull(paperTrackings.getPaperStatus().getDeliveryFailureCause());
                Assertions.assertFalse(paperTrackings.getValidationFlow().getOcrEnabled());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getSequencesValidationTimestamp());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getDematValidationTimestamp());
                Assertions.assertNull(paperTrackings.getValidationFlow().getOcrRequestTimestamp());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getRegisteredLetterCode());
                // Assertions.assertNotNull(paperTrackings.getPaperStatus().getFinalStatusCode());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getValidatedSequenceTimestamp());
            }
            case OK_CONSEGNATO_FASCICOLO_CHIUSO_011_DUPLICATO_OK -> {
                Assertions.assertEquals(DONE, paperTrackings.getState());
                Assertions.assertEquals(6, paperTrackings.getEvents().size());
                Assertions.assertEquals(5, paperTrackings.getPaperStatus().getValidatedEvents().size());
                Assertions.assertNull(paperTrackings.getNextRequestIdPcretry());
                Assertions.assertNull(paperTrackings.getPaperStatus().getDeliveryFailureCause());
                Assertions.assertFalse(paperTrackings.getValidationFlow().getOcrEnabled());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getSequencesValidationTimestamp());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getDematValidationTimestamp());
                Assertions.assertNull(paperTrackings.getValidationFlow().getOcrRequestTimestamp());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getRegisteredLetterCode());
                // Assertions.assertNotNull(paperTrackings.getPaperStatus().getFinalStatusCode());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getValidatedSequenceTimestamp());
            }
            case CONSEGNATO_FASCICOLO_CHIUSO_STATO_ASSENTE -> {
                Assertions.assertEquals(KO, paperTrackings.getState());
                Assertions.assertEquals(5, paperTrackings.getEvents().size());
                Assertions.assertNull(paperTrackings.getNextRequestIdPcretry());
            }
            case CONSEGNATO_FASCICOLO_CHIUSO_STATO_NON_INERENTE -> {
                Assertions.assertEquals(DONE, paperTrackings.getState());
                Assertions.assertEquals(4, paperTrackings.getEvents().size());
                Assertions.assertEquals(3, paperTrackings.getPaperStatus().getValidatedEvents().size());
                Assertions.assertNull(paperTrackings.getNextRequestIdPcretry());
                Assertions.assertFalse(paperTrackings.getValidationFlow().getOcrEnabled());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getSequencesValidationTimestamp());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getDematValidationTimestamp());
                Assertions.assertNull(paperTrackings.getValidationFlow().getOcrRequestTimestamp());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getRegisteredLetterCode());
                // Assertions.assertNotNull(paperTrackings.getPaperStatus().getFinalStatusCode());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getValidatedSequenceTimestamp());
            }
            case IRREPERIBILITA_ASSOLUTA_FASCICOLO_CHIUSO_STATO_NON_INERENTE -> {
                Assertions.assertEquals(DONE, paperTrackings.getState());
                Assertions.assertEquals(7, paperTrackings.getEvents().size());
                Assertions.assertEquals(5, paperTrackings.getPaperStatus().getValidatedEvents().size());
                Assertions.assertNull(paperTrackings.getNextRequestIdPcretry());
                Assertions.assertNull(paperTrackings.getPaperStatus().getDeliveryFailureCause());
                Assertions.assertFalse(paperTrackings.getValidationFlow().getOcrEnabled());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getSequencesValidationTimestamp());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getDematValidationTimestamp());
                Assertions.assertNull(paperTrackings.getValidationFlow().getOcrRequestTimestamp());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getRegisteredLetterCode());
                // Assertions.assertNotNull(paperTrackings.getPaperStatus().getFinalStatusCode());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getValidatedSequenceTimestamp());
            }
            case IRREPERIBILITA_ASSOLUTA_FASCICOLO_CHIUSO_ALLEGATI_SEPARATI -> {
                Assertions.assertEquals(DONE, paperTrackings.getState());
                Assertions.assertEquals(4, paperTrackings.getEvents().size());
                Assertions.assertEquals(4, paperTrackings.getPaperStatus().getValidatedEvents().size());
                Assertions.assertNull(paperTrackings.getNextRequestIdPcretry());
                Assertions.assertNull(paperTrackings.getPaperStatus().getDeliveryFailureCause());
                Assertions.assertFalse(paperTrackings.getValidationFlow().getOcrEnabled());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getSequencesValidationTimestamp());
                Assertions.assertNotNull(paperTrackings.getValidationFlow().getDematValidationTimestamp());
                Assertions.assertNull(paperTrackings.getValidationFlow().getOcrRequestTimestamp());
                Assertions.assertNotNull(paperTrackings.getPaperStatus().getRegisteredLetterCode());
                // Assertions.assertNotNull(paperTrackings.getPaperStatus().getFinalStatusCode());
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
