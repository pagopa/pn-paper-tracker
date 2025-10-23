package it.pagopa.pn.papertracker.middleware.dao.dynamo.entity;

public enum PaperTrackingsState {

    AWAITING_FINAL_STATUS_CODE,
    AWAITING_OCR,
    DONE,
    AWAITING_REWORK_EVENTS,
    KO
}
