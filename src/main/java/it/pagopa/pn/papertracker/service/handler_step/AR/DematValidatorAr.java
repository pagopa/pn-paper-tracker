package it.pagopa.pn.papertracker.service.handler_step.AR;

import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import it.pagopa.pn.papertracker.service.handler_step.generic.DematValidator;
import it.pagopa.pn.papertracker.utils.OcrUtility;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DematValidatorAr extends DematValidator implements HandlerStep {

    public DematValidatorAr(OcrUtility ocrUtility) {
        super(ocrUtility);
    }
}
