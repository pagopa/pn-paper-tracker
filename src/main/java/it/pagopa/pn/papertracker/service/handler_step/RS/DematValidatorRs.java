package it.pagopa.pn.papertracker.service.handler_step.RS;

import it.pagopa.pn.papertracker.service.handler_step.generic.DematValidator;
import it.pagopa.pn.papertracker.utils.OcrUtility;

public class DematValidatorRs extends DematValidator {
    public DematValidatorRs(OcrUtility ocrUtility) {
        super(ocrUtility);
    }
}
