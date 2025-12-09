package it.pagopa.pn.papertracker.exception;

import it.pagopa.pn.commons.exceptions.PnInternalException;
import org.springframework.http.HttpStatus;

public class AttachmentsConfigNotFound extends PnInternalException {

    public AttachmentsConfigNotFound(String errorCode, String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR.value(), errorCode);
    }
}
