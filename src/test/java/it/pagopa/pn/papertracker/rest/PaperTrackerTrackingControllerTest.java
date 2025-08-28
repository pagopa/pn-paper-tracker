package it.pagopa.pn.papertracker.rest;

import it.pagopa.pn.papertracker.exception.PnPaperTrackerConflictException;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingCreationRequest;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingsRequest;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingsResponse;
import it.pagopa.pn.papertracker.service.PaperTrackerTrackingService;
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
class PaperTrackerTrackingControllerTest {

    private PaperTrackerTrackingController controller;

    @Mock
    private PaperTrackerTrackingService paperTrackerEventService;

    @BeforeEach
    void setUp() {
        controller = new PaperTrackerTrackingController(paperTrackerEventService);
    }

    @Test
    void initTrackingReturnsOkResponse() {
        //ARRANGE
        TrackingCreationRequest request = new TrackingCreationRequest();
        request.setAttemptId("request123");
        request.setPcRetry("PCRETRY_0");
        request.setUnifiedDeliveryDriver("test-driver-id");
        request.setProductType("test-product-type");
        Mono<TrackingCreationRequest> requestMono = Mono.just(request);

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
        TrackingCreationRequest request = new TrackingCreationRequest();
        Mono<TrackingCreationRequest> requestMono = Mono.just(request);

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

    @Test
    void retrieveTrackingsReturnsOkResponse() {
        //ARRANGE
        TrackingsRequest request = new TrackingsRequest();
        Mono<TrackingsRequest> requestMono = Mono.just(request);
        TrackingsResponse responseMock = new TrackingsResponse();

        when(paperTrackerEventService.retrieveTrackings(request)).thenReturn(Mono.just(responseMock));

        //ACT
        Mono<ResponseEntity<TrackingsResponse>> response = controller.retrieveTrackings(requestMono, null);

        //ASSERT
        StepVerifier.create(response)
                .expectNext(ResponseEntity.ok(responseMock))
                .verifyComplete();
        verify(paperTrackerEventService, times(1)).retrieveTrackings(request);
    }

    @Test
    void retrieveTrackingsHandlesError() {
        //ARRANGE
        TrackingsRequest request = new TrackingsRequest();
        Mono<TrackingsRequest> requestMono = Mono.just(request);

        when(paperTrackerEventService.retrieveTrackings(request)).thenReturn(Mono.error(new RuntimeException("Service error")));

        //ACT
        Mono<ResponseEntity<TrackingsResponse>> response = controller.retrieveTrackings(requestMono, null);

        //ASSERT
        StepVerifier.create(response)
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException
                        && "Service error".equals(throwable.getMessage()))
                .verify();
        verify(paperTrackerEventService, times(1)).retrieveTrackings(request);
    }

    @Test
    void retrieveTrackingsByAttemptIdReturnsOkResponse() {
        //ARRANGE
        TrackingsResponse responseMock = new TrackingsResponse();

        when(paperTrackerEventService.retrieveTrackingsByAttemptId("attemptId", "pcRetry")).thenReturn(Mono.just(responseMock));

        //ACT
        Mono<ResponseEntity<TrackingsResponse>> response = controller.retrieveTrackingsByAttemptId("attemptId", "pcRetry", null);

        //ASSERT
        StepVerifier.create(response)
                .expectNext(ResponseEntity.ok(responseMock))
                .verifyComplete();
        verify(paperTrackerEventService, times(1)).retrieveTrackingsByAttemptId("attemptId", "pcRetry");
    }

    @Test
    void retrieveTrackingsByAttemptIdHandlesError() {
        //ARRANGE
        TrackingsRequest request = new TrackingsRequest();

        when(paperTrackerEventService.retrieveTrackingsByAttemptId("attemptId", "pcRetry")).thenReturn(Mono.error(new RuntimeException("Service error")));

        //ACT
        Mono<ResponseEntity<TrackingsResponse>> response = controller.retrieveTrackingsByAttemptId("attemptId", "pcRetry", null);

        //ASSERT
        StepVerifier.create(response)
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException
                        && "Service error".equals(throwable.getMessage()))
                .verify();
        verify(paperTrackerEventService, times(1)).retrieveTrackingsByAttemptId("attemptId", "pcRetry");
    }
}
