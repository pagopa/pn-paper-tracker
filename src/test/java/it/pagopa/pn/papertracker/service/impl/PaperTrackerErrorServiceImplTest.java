package it.pagopa.pn.papertracker.service.impl;

import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingErrorsResponse;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingsRequest;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsErrorsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsErrors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaperTrackerErrorServiceImplTest {

    @Mock
    private PaperTrackingsErrorsDAO paperTrackingsErrorsDAO;

    private PaperTrackerErrorServiceImpl paperTrackerErrorService;

    @BeforeEach
    void setUp() {
        paperTrackerErrorService = new PaperTrackerErrorServiceImpl(paperTrackingsErrorsDAO);
    }

    @Test
    void retrieveTrackingErrorsReturnsResponseWithErrors() {
        TrackingsRequest request = new TrackingsRequest();
        request.setTrackingIds(List.of("tracking1", "tracking2"));
        PaperTrackingsErrors paperTrackingsErrors1 = new PaperTrackingsErrors();
        paperTrackingsErrors1.setRequestId("tracking1");
        PaperTrackingsErrors paperTrackingsErrors2 = new PaperTrackingsErrors();
        paperTrackingsErrors2.setRequestId("tracking2");

        when(paperTrackingsErrorsDAO.retrieveErrors("tracking1"))
                .thenReturn(Flux.just(paperTrackingsErrors1));
        when(paperTrackingsErrorsDAO.retrieveErrors("tracking2"))
                .thenReturn(Flux.just(paperTrackingsErrors2));

        Mono<TrackingErrorsResponse> response = paperTrackerErrorService.retrieveTrackingErrors(request);

        StepVerifier.create(response)
                .expectNextMatches(res -> res.getResults().getFirst().getTrackingId().equals("tracking1") &&
                        res.getResults().getLast().getTrackingId().equals("tracking2"))
                .verifyComplete();
        verify(paperTrackingsErrorsDAO, times(1)).retrieveErrors("tracking1");
        verify(paperTrackingsErrorsDAO, times(1)).retrieveErrors("tracking2");
    }

    @Test
    void retrieveTrackingErrorsHandlesEmptyTrackingIds() {
        TrackingsRequest request = new TrackingsRequest();
        request.setTrackingIds(Collections.emptyList());

        Mono<TrackingErrorsResponse> response = paperTrackerErrorService.retrieveTrackingErrors(request);

        StepVerifier.create(response)
                .expectNextMatches(res -> res.getResults().isEmpty())
                .verifyComplete();
        verifyNoInteractions(paperTrackingsErrorsDAO);
    }

    @Test
    void retrieveTrackingErrorsHandlesErrorFromDAO() {
        TrackingsRequest request = new TrackingsRequest();
        request.setTrackingIds(List.of("tracking1"));

        when(paperTrackingsErrorsDAO.retrieveErrors("tracking1"))
                .thenReturn(Flux.error(new RuntimeException("DAO error")));

        Mono<TrackingErrorsResponse> response = paperTrackerErrorService.retrieveTrackingErrors(request);

        StepVerifier.create(response)
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException
                        && "DAO error".equals(throwable.getMessage()))
                .verify();
        verify(paperTrackingsErrorsDAO, times(1)).retrieveErrors("tracking1");
    }
}
