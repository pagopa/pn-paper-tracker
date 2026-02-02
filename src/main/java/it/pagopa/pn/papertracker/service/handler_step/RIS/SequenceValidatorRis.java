package it.pagopa.pn.papertracker.service.handler_step.RIS;

import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsErrorsDAO;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import it.pagopa.pn.papertracker.service.handler_step.generic.GenericSequenceValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SequenceValidatorRis extends GenericSequenceValidator implements HandlerStep {

    public SequenceValidatorRis(PaperTrackingsDAO paperTrackingsDAO, PaperTrackingsErrorsDAO paperTrackingsErrorsDAO) {
        super(paperTrackingsDAO, paperTrackingsErrorsDAO);
    }
}
