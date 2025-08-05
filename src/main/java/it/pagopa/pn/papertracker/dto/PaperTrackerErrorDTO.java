package it.pagopa.pn.papertracker.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class PaperTrackerErrorDTO {
    private String requestId;
    private Instant created;
    private String category;
    private Details details;
    private String flowThrow;
    private String eventThrow;
    private String productType;

    @Data
    @Builder
    public static class Details {
        private String cause;
        private String message;
    }
}