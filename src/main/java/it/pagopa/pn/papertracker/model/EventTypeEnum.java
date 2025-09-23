package it.pagopa.pn.papertracker.model;

public enum EventTypeEnum {
    INTERMEDIATE_EVENT,
    RETRYABLE_EVENT,
    NOT_RETRYABLE_EVENT,
    FINAL_EVENT,
    SAVE_ONLY_EVENT,
    OCR_RESPONSE_EVENT
}