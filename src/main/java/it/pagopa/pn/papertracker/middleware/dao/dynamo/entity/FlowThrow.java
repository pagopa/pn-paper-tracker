package it.pagopa.pn.papertracker.middleware.dao.dynamo.entity;

public enum FlowThrow {

    SEQUENCE_VALIDATION,
    DEMAT_VALIDATION,
    FINAL_EVENT_BUILDING,
    RETRY_PHASE;

}