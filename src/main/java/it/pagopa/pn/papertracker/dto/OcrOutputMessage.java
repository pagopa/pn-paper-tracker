package it.pagopa.pn.papertracker.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OcrOutputMessage {
    private String version;
    private String CommandId;
    private String commandType;
    private DataField data;

    @Data
    @Builder
    public static class DataField {
        private ValidationType validationType;
        private ValidationStatus validationStatus;
        private String description;
    }

    public enum ValidationType {
        operator, ai
    }

    public enum ValidationStatus {
        PENDING, KO
    }
}