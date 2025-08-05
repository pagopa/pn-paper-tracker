package it.pagopa.pn.papertracker.middleware.queue.consumer.internal;

import com.sngular.apigenerator.asyncapi.business_model.model.event.OcrDataResultPayload;
import io.netty.util.internal.StringUtil;
import it.pagopa.pn.papertracker.exception.PaperTrackerOcrKoException;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.utils.BuilderUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class InternalEventHandler {

    private static final String OCR_OK = "OK";
    private static final String OCR_KO = "KO";

    private final PaperTrackingsDAO paperTrackingsDAO;

    public void handleOcrMessage(OcrDataResultPayload ocrResultMessage) {

        String tripletta = StringUtil.EMPTY_STRING;
        String productType = StringUtil.EMPTY_STRING;
        String requestId = StringUtil.EMPTY_STRING;
        log.debug("Handle message from pn-ocr_outputs with content {}", ocrResultMessage);
        paperTrackingsDAO.retrieveEntityByOcrRequestId(ocrResultMessage.getCommandId())
                .map(paperTrackings -> {
                    if (OCR_OK.equals(ocrResultMessage.getData().getValidationStatus().getValue())) {
                        return paperTrackingsDAO.updateItem(paperTrackings.getTrackingId(), buildPaperTrackings(paperTrackings));
                        //TODO richiamare metodo di costruzione evento finale
                    } else if (OCR_KO.equals(ocrResultMessage.getData().getValidationStatus().getValue())) {
                        throw new PaperTrackerOcrKoException("Ocr KO!", BuilderUtils.buildErrorTrackerDTO(ocrResultMessage, requestId, productType, tripletta));
                    } else {
                        log.warn("Received OCR result with unknown type: {}", ocrResultMessage.getCommandType());
                        return Mono.empty();
                    }
                });
    }

    private PaperTrackings buildPaperTrackings(PaperTrackings paperTrackings) {
        paperTrackings.getValidationFlow().setDematValidationTimestamp(Instant.now());
        return paperTrackings;
    }
}
