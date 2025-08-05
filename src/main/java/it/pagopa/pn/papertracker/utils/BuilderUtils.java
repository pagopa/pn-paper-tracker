package it.pagopa.pn.papertracker.utils;

import com.sngular.apigenerator.asyncapi.business_model.model.event.OcrDataResultPayload;
import it.pagopa.pn.papertracker.dto.PaperTrackerErrorDTO;

import java.time.Instant;

public class BuilderUtils {
    public static PaperTrackerErrorDTO buildErrorTrackerDTO(OcrDataResultPayload ocrResultMessage, String requestId, String productType, String tripletta) {
        return PaperTrackerErrorDTO.builder()
                .requestId(requestId)
                .created(Instant.now())
                .category("OCR")
                .details(PaperTrackerErrorDTO.Details.builder().cause("OCR_KO").message(ocrResultMessage.getData().getDescription()).build())
                .flowThrow("DEMAT_VALIDATION")
                .eventThrow(tripletta)
                .productType(productType)
                .build();
    }
}
