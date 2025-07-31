package it.pagopa.pn.papertracker.rest;

import it.pagopa.pn.papertracker.exception.PnPaperTrackerConflictException;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackerCreationRequest;
import it.pagopa.pn.papertracker.service.PaperTrackerEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaperTrackerEventControllerTest {

    private PaperTrackerEventController controller;

    @Mock
    private PaperTrackerEventService paperTrackerEventService;

    @BeforeEach
    void setUp() {
        controller = new PaperTrackerEventController(paperTrackerEventService);
    }

    @Test
    void initTrackingReturnsOkResponse() {
        //ARRANGE
        TrackerCreationRequest request = new TrackerCreationRequest();
        request.setRequestId("test-request-id");
        request.setUnifiedDeliveryDriver("test-driver-id");
        request.setProductType("test-product-type");
        Mono<TrackerCreationRequest> requestMono = Mono.just(request);

        when(paperTrackerEventService.insertPaperTrackings(request)).thenReturn(Mono.empty());

        //ACT
        Mono<ResponseEntity<Void>> response = controller.initTracking(requestMono, null);

        //ASSERT
        StepVerifier.create(response)
                .expectNext(ResponseEntity.status(HttpStatus.CREATED).build())
                .verifyComplete();
        verify(paperTrackerEventService, times(1)).insertPaperTrackings(request);
    }

    @Test
    void initTrackingReturnsConflict() {
        //ARRANGE
        TrackerCreationRequest request = new TrackerCreationRequest();
        Mono<TrackerCreationRequest> requestMono = Mono.just(request);

        when(paperTrackerEventService.insertPaperTrackings(request)).thenReturn(Mono.error(new PnPaperTrackerConflictException("Duplicated item", "Conflict")));

        //ACT
        Mono<ResponseEntity<Void>> response = controller.initTracking(requestMono, null);

        //ASSERT
        StepVerifier.create(response)
                .expectErrorMatches(throwable -> throwable instanceof PnPaperTrackerConflictException
                        && "Conflict".equals(throwable.getMessage()))
                .verify();
        verify(paperTrackerEventService, times(1)).insertPaperTrackings(request);
    }
}
