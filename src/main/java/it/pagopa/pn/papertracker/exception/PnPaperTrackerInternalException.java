package it.pagopa.pn.papertracker.exception;

import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsErrors;

public class PnPaperTrackerInternalException extends RuntimeException {

    public PnPaperTrackerInternalException(String message, PaperTrackingsErrors error) {
        super(message);
    }
}
