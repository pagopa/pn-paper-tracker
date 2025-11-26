package it.pagopa.pn.papertracker.exception;

import it.pagopa.pn.commons.exceptions.PnInternalException;
import org.springframework.http.HttpStatus;

public class PnPaperTrackerBadRequestException extends PnInternalException {

    public PnPaperTrackerBadRequestException(String errorCode, String message) {
        super(message, HttpStatus.BAD_REQUEST.value(), errorCode);
    }
}
