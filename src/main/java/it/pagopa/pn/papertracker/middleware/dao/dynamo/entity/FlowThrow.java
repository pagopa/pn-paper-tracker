package it.pagopa.pn.papertracker.middleware.dao.dynamo.entity;

public enum FlowThrow {

    INIT_TRACKING,
    NOT_RETRYABLE_EVENT_HANDLING,
    DUPLICATED_EVENT_VALIDATION,
    SEQUENCE_VALIDATION,
    DEMAT_VALIDATION,
    FINAL_EVENT_BUILDING,
    RETRY_PHASE;

}