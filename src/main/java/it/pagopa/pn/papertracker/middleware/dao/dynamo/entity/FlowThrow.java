package it.pagopa.pn.papertracker.middleware.dao.dynamo.entity;

public enum FlowThrow {

    INIT_TRACKING,
    DUPLICATED_EVENT_VALIDATION,
    SEQUENCE_VALIDATION,
    DEMAT_VALIDATION,
    FINAL_EVENT_BUILDING,
    RETRY_PHASE;

}