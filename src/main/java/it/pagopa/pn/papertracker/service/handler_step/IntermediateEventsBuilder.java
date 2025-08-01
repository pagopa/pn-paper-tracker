package it.pagopa.pn.papertracker.service.handler_step;

import it.pagopa.pn.papertracker.mapper.SendEventMapper;
import it.pagopa.pn.papertracker.model.HandlerContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Mono;

import java.util.ArrayList;

@Component
@RequiredArgsConstructor
public class IntermediateEventsBuilder implements HandlerStep {

    @Override
    public Mono<Void> execute(HandlerContext context) {
        return SendEventMapper.createSendEventsFromPaperProgressStatusEvent(context)
                .flatMap(sendEvent -> {
                    if(CollectionUtils.isEmpty(context.getEventsToSend())) {
                        context.setEventsToSend(new ArrayList<>());
                        context.getEventsToSend().add(sendEvent);
                    } else {
                        context.getEventsToSend().add(sendEvent);
                    }
                    return Mono.empty();
                }).then();
    }

}