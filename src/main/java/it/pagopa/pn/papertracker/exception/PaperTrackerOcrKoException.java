package it.pagopa.pn.papertracker.exception;

import it.pagopa.pn.papertracker.dto.PaperTrackerErrorDTO;

public class PaperTrackerOcrKoException extends RuntimeException {

    public PaperTrackerOcrKoException(String message, PaperTrackerErrorDTO errorDTO) {
        super(message);
        putErrorDTO(errorDTO);
    }

    private void putErrorDTO (PaperTrackerErrorDTO errorDTO) {
        // TODO implement logic to store the errorDTO
    }
}