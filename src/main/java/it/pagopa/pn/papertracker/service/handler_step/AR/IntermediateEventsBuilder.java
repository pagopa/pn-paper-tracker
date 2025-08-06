package it.pagopa.pn.papertracker.service.handler_step.AR;

import it.pagopa.pn.papertracker.mapper.SendEventMapper;
import it.pagopa.pn.papertracker.model.HandlerContext;
import it.pagopa.pn.papertracker.service.handler_step.HandlerStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class IntermediateEventsBuilder implements HandlerStep {

    @Override
    public Mono<Void> execute(HandlerContext context) {
        return SendEventMapper.createSendEventsFromPaperProgressStatusEvent(context.getPaperProgressStatusEvent())
                .doOnNext(sendEvent -> context.getEventsToSend().add(sendEvent))
                .then();
    }

}