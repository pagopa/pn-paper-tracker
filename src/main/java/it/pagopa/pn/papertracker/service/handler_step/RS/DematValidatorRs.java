package it.pagopa.pn.papertracker.service.handler_step.RS;

import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import it.pagopa.pn.papertracker.service.handler_step.generic.DematValidator;
import it.pagopa.pn.papertracker.utils.OcrUtility;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static it.pagopa.pn.papertracker.model.EventStatusCodeEnum.RECRS001C;
import static it.pagopa.pn.papertracker.model.EventStatusCodeEnum.RECRS003C;

@Service
@Slf4j
public class DematValidatorRs extends DematValidator implements HandlerStep {

    public DematValidatorRs(OcrUtility ocrUtility) {
        super(ocrUtility);
    }

    @Override
    public Mono<Void> execute(HandlerContext context) {
        log.info("Starting DematValidatorRs execution for trackingId={}", context.getTrackingId());
        String statusCode = context.getPaperProgressStatusEvent().getStatusCode();
        if (RECRS001C.name().equals(statusCode) || RECRS003C.name().equals(statusCode)) {
            log.info("StatusCode excluded from validateDemat: {}", statusCode);
            return Mono.empty();
        }
        return validateDemat(context);
    }
}