package it.pagopa.pn.papertracker.service.handler_step.RS;

import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import it.pagopa.pn.papertracker.service.handler_step.generic.GenericDematValidator;
import it.pagopa.pn.papertracker.utils.OcrUtility;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DematValidatorRs extends GenericDematValidator implements HandlerStep {

    public DematValidatorRs(OcrUtility ocrUtility) {
        super(ocrUtility);
    }
}
