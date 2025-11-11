package it.pagopa.pn.papertracker.service.handler_step._890;

import it.pagopa.pn.papertracker.config.SequenceConfiguration;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import it.pagopa.pn.papertracker.service.handler_step.generic.GenericSequenceValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class SequenceValidator890 extends GenericSequenceValidator implements HandlerStep {

    public SequenceValidator890(SequenceConfiguration sequenceConfiguration, PaperTrackingsDAO paperTrackingsDAO) {
        super(sequenceConfiguration, paperTrackingsDAO);
    }

    @Override
    public Mono<Void> execute(HandlerContext context) {
        return Mono.empty();
    }
}
