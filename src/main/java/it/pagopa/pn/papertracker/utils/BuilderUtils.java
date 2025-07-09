package it.pagopa.pn.papertracker.utils;

import com.sngular.apigenerator.asyncapi.business_model.model.event.Ocr_data_result_payload;
import it.pagopa.pn.papertracker.dto.PaperTrackerErrorDTO;

import java.time.Instant;

public class BuilderUtils {
    public static PaperTrackerErrorDTO buildErrorTrackerDTO(Ocr_data_result_payload ocrResultMessage, String requestId, String productType, String tripletta) {
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
