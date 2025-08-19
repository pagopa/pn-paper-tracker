package it.pagopa.pn.papertracker.service.handler_step.RIR;

import it.pagopa.pn.papertracker.config.StatusCodeConfiguration;
import it.pagopa.pn.papertracker.middleware.msclient.DataVaultClient;
import it.pagopa.pn.papertracker.service.handler_step.GenericFinalEventBuilder;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FinalEventBuilderRir extends GenericFinalEventBuilder implements HandlerStep {

    public FinalEventBuilderRir(StatusCodeConfiguration statusCodeConfiguration, DataVaultClient dataVaultClient) {
        super(statusCodeConfiguration, dataVaultClient);
    }
}
