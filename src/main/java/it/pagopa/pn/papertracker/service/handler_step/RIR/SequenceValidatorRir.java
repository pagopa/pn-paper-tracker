package it.pagopa.pn.papertracker.service.handler_step.RIR;

import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsErrorsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ErrorCategory;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Event;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.model.DeliveryFailureCauseEnum;
import it.pagopa.pn.papertracker.model.EventStatusCodeEnum;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import it.pagopa.pn.papertracker.service.handler_step.generic.GenericSequenceValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.List;

import static it.pagopa.pn.papertracker.model.EventStatusCodeEnum.RECRI004A;

@Component
@Slf4j
public class SequenceValidatorRir extends GenericSequenceValidator implements HandlerStep {

    public SequenceValidatorRir(PaperTrackingsDAO paperTrackingsDAO, PaperTrackingsErrorsDAO paperTrackingsErrorsDAO) {
        super(paperTrackingsDAO, paperTrackingsErrorsDAO);
    }


    @Override
    protected Mono<Event> validateSingleDeliveryFailureCause(Event event, PaperTrackings paperTrackings, HandlerContext context, Boolean strictFinalEventValidation) {
        log.info("Beginning RIR validation for delivery failure cause for event : {}", event);

        EventStatusCodeEnum statusCodeEnum = EventStatusCodeEnum.fromKey(event.getStatusCode());

        if (statusCodeEnum.equals(RECRI004A)) {

            String deliveryFailureCause = event.getDeliveryFailureCause();
            List<DeliveryFailureCauseEnum> allowedCauses = statusCodeEnum.getDeliveryFailureCauseList();

            if (!StringUtils.hasText(deliveryFailureCause)) {
                // Se la causa non è valorizzata, vado avanti
                return Mono.just(event);
            }

            if (allowedCauses.contains(DeliveryFailureCauseEnum.fromValue(deliveryFailureCause))) {
                // Se valorizzata ed è tra le allowed, vado avanti
                return Mono.just(event);
            }

            //TODO: aggiornare con additionalDetail dopo merge

            // Se valorizzata ma non tra le allowed, errore/warning
            return getErrorOrSaveWarning(
                    "Invalid deliveryFailureCause: " + deliveryFailureCause,
                    context,
                    paperTrackings,
                    ErrorCategory.DELIVERY_FAILURE_CAUSE_ERROR,
                    strictFinalEventValidation,
                    event
            );
        }
        // Per tutti gli altri casi, delega alla superclasse
        return super.validateSingleDeliveryFailureCause(event, paperTrackings, context, strictFinalEventValidation);
    }
}