package it.pagopa.pn.papertracker.middleware.dao.dynamo.entity;

public enum PaperTrackingsState {
    AWAITING_REFINEMENT,
    AWAITING_OCR,
    DONE,
    KO
}
