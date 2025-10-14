package it.pagopa.pn.papertracker.rest;

import it.pagopa.pn.papertracker.exception.PnPaperTrackerBadRequestException;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.SequenceResponse;
import it.pagopa.pn.papertracker.service.NotificationReworkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.Mockito.*;

class NotificationReworkControllerTest {

    private static final String ERROR_CODE_PAPER_TRACKER_BAD_REQUEST = "PN_PAPER_TRACKER_BAD_REQUEST";
    private NotificationReworkService notificationReworkService;
    private NotificationReworkController controller;
    private ServerWebExchange exchange;

    @BeforeEach
    void setUp() {
        notificationReworkService = mock(NotificationReworkService.class);
        controller = new NotificationReworkController(notificationReworkService);
        exchange = mock(ServerWebExchange.class);
    }

    @Test
    void notificationRework_shouldReturnOkResponse() {
        String statusCode = "RECRN005C";
        String deliveryFailureCause = "M02";
        SequenceResponse response = new SequenceResponse();
        response.setFinalStatusCode(SequenceResponse.FinalStatusCodeEnum.OK);
        response.setSequence(List.of("RECRN001P", "RECRN002F", "RECRN003D", "RECRN004A", "RECRN005C"));
        when(notificationReworkService.notificationRework(statusCode, deliveryFailureCause))
                .thenReturn(Mono.just(response));

        Mono<ResponseEntity<SequenceResponse>> result = controller.notificationRework(statusCode, deliveryFailureCause, exchange);

        StepVerifier.create(result)
                .expectNextMatches(entity -> entity.getStatusCode().is2xxSuccessful()
                        && entity.getBody().getSequence().equals(List.of("RECRN001P", "RECRN002F", "RECRN003D", "RECRN004A", "RECRN005C"))
                        && entity.getBody().getFinalStatusCode() == SequenceResponse.FinalStatusCodeEnum.OK)
                .verifyComplete();

        verify(notificationReworkService, times(1)).notificationRework(statusCode, deliveryFailureCause);
    }

    @Test
    void notificationRework_shouldPropagateError() {
        String statusCode = "INVALID";
        String deliveryFailureCause = "M02";
        when(notificationReworkService.notificationRework(statusCode, deliveryFailureCause))
                .thenReturn(Mono.error(new PnPaperTrackerBadRequestException(ERROR_CODE_PAPER_TRACKER_BAD_REQUEST, String.format("statusCode %s is invalid", statusCode))));

        Mono<ResponseEntity<SequenceResponse>> result = controller.notificationRework(statusCode, deliveryFailureCause, exchange);

        StepVerifier.create(result)
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerBadRequestException )
                .verify();

        verify(notificationReworkService, times(1)).notificationRework(statusCode, deliveryFailureCause);
    }
}