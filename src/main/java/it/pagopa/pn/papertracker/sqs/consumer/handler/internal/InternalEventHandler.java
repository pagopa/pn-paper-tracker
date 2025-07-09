package it.pagopa.pn.papertracker.sqs.consumer.handler.internal;

import com.sngular.apigenerator.asyncapi.business_model.model.event.Ocr_data_result_payload;
import io.netty.util.internal.StringUtil;
import it.pagopa.pn.papertracker.exception.PaperTrackerOcrKoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import it.pagopa.pn.papertracker.utils.BuilderUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class InternalEventHandler {

    private static final String OCR_OK = "OK";
    private static final String OCR_KO = "KO";

    public void handleMessage(Ocr_data_result_payload ocrResultMessage) {

        String tripletta = StringUtil.EMPTY_STRING;
        String productType = StringUtil.EMPTY_STRING;
        String requestId = StringUtil.EMPTY_STRING;

        try {
            log.debug("Handle message from pn-ocr_outputs with content {}", ocrResultMessage);
            //TODO eseguire una query sull’indice ocrRequestId-index della tabella pn-PaperTracking utilizzando come pk il commandId presente nell’evento

            if (OCR_OK.equals(ocrResultMessage.getData().getValidationStatus().getValue())) {
                //TODO eseguire un updateItem dell’entità recuperata al punto 1 aggiornando i seguenti attributi:
                //  validationFlow.dematValidationTimestamp
                //TODO richiamare metodo di costruzione evento finale
            } else if (OCR_KO.equals(ocrResultMessage.getData().getValidationStatus().getValue())) {
                throw new PaperTrackerOcrKoException("Ocr KO!", BuilderUtils.buildErrorTrackerDTO(ocrResultMessage, requestId, productType, tripletta));
            } else {
                log.warn("Received OCR result with unknown type: {}", ocrResultMessage.getCommandType());
            }

        } catch (Exception ex) {
            log.error("Error processing OCR result message: {}", ex.getMessage(), ex);
        }
    }
}
