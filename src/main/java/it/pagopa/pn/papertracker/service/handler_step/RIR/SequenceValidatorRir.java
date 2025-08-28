package it.pagopa.pn.papertracker.service.handler_step.RIR;

import it.pagopa.pn.papertracker.config.SequenceConfiguration;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.service.handler_step.generic.GenericSequenceValidator;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SequenceValidatorRir extends GenericSequenceValidator implements HandlerStep {

    public SequenceValidatorRir(SequenceConfiguration sequenceConfiguration, PaperTrackingsDAO paperTrackingsDAO) {
        super(sequenceConfiguration, paperTrackingsDAO);
    }
}
