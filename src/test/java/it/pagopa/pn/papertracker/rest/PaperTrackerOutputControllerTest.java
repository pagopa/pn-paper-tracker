package it.pagopa.pn.papertracker.rest;

import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.PaperTrackerOutputsResponse;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingsRequest;
import it.pagopa.pn.papertracker.service.PaperTrackerOutputService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaperTrackerOutputControllerTest {

    @Mock
    private PaperTrackerOutputService paperTrackerOutputService;

    private PaperTrackerOutputController controller;

    @BeforeEach
    void setUp() {
        controller = new PaperTrackerOutputController(paperTrackerOutputService);
    }

    @Test
    void retrieveTrackingOutputsReturnsOkResponse() {
        TrackingsRequest request = new TrackingsRequest();
        Mono<TrackingsRequest> requestMono = Mono.just(request);
        PaperTrackerOutputsResponse responseMock = new PaperTrackerOutputsResponse();

        when(paperTrackerOutputService.retrieveTrackingOutputs(request)).thenReturn(Mono.just(responseMock));

        Mono<ResponseEntity<PaperTrackerOutputsResponse>> response = controller.retrieveTrackingOutputs(requestMono, null);

        StepVerifier.create(response)
                .expectNext(ResponseEntity.ok(responseMock))
                .verifyComplete();
        verify(paperTrackerOutputService, times(1)).retrieveTrackingOutputs(request);
    }

    @Test
    void retrieveTrackingOutputsHandlesError() {
        TrackingsRequest request = new TrackingsRequest();
        Mono<TrackingsRequest> requestMono = Mono.just(request);

        when(paperTrackerOutputService.retrieveTrackingOutputs(request)).thenReturn(Mono.error(new RuntimeException("Service error")));

        Mono<ResponseEntity<PaperTrackerOutputsResponse>> response = controller.retrieveTrackingOutputs(requestMono, null);

        StepVerifier.create(response)
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException
                        && "Service error".equals(throwable.getMessage()))
                .verify();
        verify(paperTrackerOutputService, times(1)).retrieveTrackingOutputs(request);
    }
}
