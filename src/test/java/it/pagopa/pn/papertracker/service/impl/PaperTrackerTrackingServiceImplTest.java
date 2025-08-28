package it.pagopa.pn.papertracker.service.impl;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerConflictException;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingCreationRequest;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingsRequest;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingsResponse;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsErrorsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.service.mapper.PaperTrackingsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaperTrackerTrackingServiceImplTest {

    @Mock
    private PaperTrackingsDAO paperTrackingsDAO;

    @Mock
    private PaperTrackingsErrorsDAO paperTrackingsErrorsDAO;

    @Mock
    private PnPaperTrackerConfigs pnPaperTrackerConfigs;

    private PaperTrackerTrackingServiceImpl paperTrackerEventService;

    @BeforeEach
    void setUp() {
        paperTrackerEventService = new PaperTrackerTrackingServiceImpl(paperTrackingsDAO, pnPaperTrackerConfigs);
    }

    @Test
    void insertPaperTrackingsValidRequest() {
        //ARRANGE
        TrackingCreationRequest request = getTrackerCreationRequest();

        when(paperTrackingsDAO.putIfAbsent(argThat(pt ->
                pt.getTrackingId().equals(String.join(".", request.getAttemptId(), request.getPcRetry())) &&
                        pt.getUnifiedDeliveryDriver().equals(request.getUnifiedDeliveryDriver()) &&
                        pt.getProductType() == ProductType.RS
        ))).thenReturn(Mono.just(new PaperTrackings()));

        //ACT
        Mono<Void> response = paperTrackerEventService.insertPaperTrackings(request);

        //ASSERT
        StepVerifier.create(response)
                .verifyComplete();
        verify(paperTrackingsDAO, times(1)).putIfAbsent(argThat(pt ->
                pt.getTrackingId().equals(String.join(".", request.getAttemptId(), request.getPcRetry())) &&
                        pt.getUnifiedDeliveryDriver().equals(request.getUnifiedDeliveryDriver()) &&
                        pt.getProductType() == ProductType.RS
        ));
    }

    @Test
    void insertPaperTrackingsConflictException() {
        //ARRANGE
        TrackingCreationRequest request = getTrackerCreationRequest();

        when(paperTrackingsDAO.putIfAbsent(argThat(pt ->
                pt.getTrackingId().equals(String.join(".", request.getAttemptId(), request.getPcRetry())) &&
                        pt.getUnifiedDeliveryDriver().equals(request.getUnifiedDeliveryDriver()) &&
                        pt.getProductType() == ProductType.RS
        ))).thenReturn(Mono.error(new PnPaperTrackerConflictException("", "")));

        //ACT
        Mono<Void> response = paperTrackerEventService.insertPaperTrackings(request);

        //ASSERT
        StepVerifier.create(response)
                .expectError(PnPaperTrackerConflictException.class)
                .verify();
        verify(paperTrackingsDAO, times(1)).putIfAbsent(argThat(pt ->
                pt.getTrackingId().equals(String.join(".", request.getAttemptId(), request.getPcRetry())) &&
                        pt.getUnifiedDeliveryDriver().equals(request.getUnifiedDeliveryDriver()) &&
                        pt.getProductType() == ProductType.RS
        ));
    }

    @Test
    void retrieveTrackingsReturnsResponseWithTrackings() {
        TrackingsRequest request = new TrackingsRequest();
        request.setTrackingIds(List.of("tracking1", "tracking2"));
        PaperTrackings paperTracking1 = new PaperTrackings();
        PaperTrackings paperTracking2 = new PaperTrackings();
        TrackingsResponse expectedResponse = new TrackingsResponse();
        expectedResponse.setTrackings(List.of(PaperTrackingsMapper.toTracking(paperTracking1), PaperTrackingsMapper.toTracking(paperTracking2)));

        when(paperTrackingsDAO.retrieveAllByTrackingIds(request.getTrackingIds()))
                .thenReturn(Flux.just(paperTracking1, paperTracking2));

        Mono<TrackingsResponse> response = paperTrackerEventService.retrieveTrackings(request);

        StepVerifier.create(response)
                .expectNextMatches(res -> res.getTrackings().equals(expectedResponse.getTrackings()))
                .verifyComplete();
        verify(paperTrackingsDAO, times(1)).retrieveAllByTrackingIds(request.getTrackingIds());
    }

    @Test
    void retrieveTrackingsReturnsResponseWithoutTrackings() {
        TrackingsRequest request = new TrackingsRequest();
        request.setTrackingIds(List.of("tracking1", "tracking2"));

        when(paperTrackingsDAO.retrieveAllByTrackingIds(request.getTrackingIds()))
                .thenReturn(Flux.empty());

        Mono<TrackingsResponse> response = paperTrackerEventService.retrieveTrackings(request);

        StepVerifier.create(response)
                .expectNextMatches(res -> CollectionUtils.isEmpty(res.getTrackings()))
                .verifyComplete();
        verify(paperTrackingsDAO, times(1)).retrieveAllByTrackingIds(request.getTrackingIds());
    }



    @Test
    void retrieveTrackingsHandlesEmptyTrackingIds() {
        TrackingsRequest request = new TrackingsRequest();
        request.setTrackingIds(Collections.emptyList());

        when(paperTrackingsDAO.retrieveAllByTrackingIds(request.getTrackingIds()))
                .thenReturn(Flux.empty());

        Mono<TrackingsResponse> response = paperTrackerEventService.retrieveTrackings(request);

        StepVerifier.create(response)
                .expectNextMatches(res -> res.getTrackings().isEmpty())
                .verifyComplete();
        verify(paperTrackingsDAO, times(1)).retrieveAllByTrackingIds(request.getTrackingIds());
    }

    @Test
    void retrieveTrackingsHandlesErrorFromDAO() {
        TrackingsRequest request = new TrackingsRequest();
        request.setTrackingIds(List.of("tracking1"));

        when(paperTrackingsDAO.retrieveAllByTrackingIds(request.getTrackingIds()))
                .thenReturn(Flux.error(new RuntimeException("DAO error")));

        Mono<TrackingsResponse> response = paperTrackerEventService.retrieveTrackings(request);

        StepVerifier.create(response)
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException
                        && "DAO error".equals(throwable.getMessage()))
                .verify();
        verify(paperTrackingsDAO, times(1)).retrieveAllByTrackingIds(request.getTrackingIds());
    }

    @Test
    void retrieveTrackingsByAttemptIdReturnsResponseWithTrackings() {
        String attemptId = "attempt123";
        String pcRetry = "PCRETRY_0";
        PaperTrackings paperTracking1 = new PaperTrackings();
        PaperTrackings paperTracking2 = new PaperTrackings();
        TrackingsResponse expectedResponse = new TrackingsResponse();
        expectedResponse.setTrackings(List.of(PaperTrackingsMapper.toTracking(paperTracking1), PaperTrackingsMapper.toTracking(paperTracking2)));

        when(paperTrackingsDAO.retrieveEntityByAttemptId(attemptId, pcRetry))
                .thenReturn(Flux.just(paperTracking1, paperTracking2));

        Mono<TrackingsResponse> response = paperTrackerEventService.retrieveTrackingsByAttemptId(attemptId, pcRetry);

        StepVerifier.create(response)
                .expectNextMatches(res -> res.getTrackings().equals(expectedResponse.getTrackings()))
                .verifyComplete();
        verify(paperTrackingsDAO, times(1)).retrieveEntityByAttemptId(attemptId, pcRetry);
    }

    @Test
    void retrieveTrackingsByAttemptIdReturnsResponseWithoutTrackings() {
        String attemptId = "attempt123";
        String pcRetry = "PCRETRY_0";

        when(paperTrackingsDAO.retrieveEntityByAttemptId(attemptId, pcRetry))
                .thenReturn(Flux.empty());

        Mono<TrackingsResponse> response = paperTrackerEventService.retrieveTrackingsByAttemptId(attemptId, pcRetry);

        StepVerifier.create(response)
                .expectNextMatches(res -> CollectionUtils.isEmpty(res.getTrackings()))
                .verifyComplete();
        verify(paperTrackingsDAO, times(1)).retrieveEntityByAttemptId(attemptId, pcRetry);
    }

    @Test
    void retrieveTrackingsByAttemptIdHandlesEmptyTrackingIds() {
        String attemptId = "attempt123";
        String pcRetry = "PCRETRY_0";

        when(paperTrackingsDAO.retrieveEntityByAttemptId(attemptId, pcRetry))
                .thenReturn(Flux.empty());

        Mono<TrackingsResponse> response = paperTrackerEventService.retrieveTrackingsByAttemptId(attemptId, pcRetry);

        StepVerifier.create(response)
                .expectNextMatches(res -> res.getTrackings().isEmpty())
                .verifyComplete();
        verify(paperTrackingsDAO, times(1)).retrieveEntityByAttemptId(attemptId, pcRetry);
    }

    @Test
    void retrieveTrackingsByAttemptIdHandlesErrorFromDAO() {
        String attemptId = "attempt123";
        String pcRetry = "PCRETRY_0";

        when(paperTrackingsDAO.retrieveEntityByAttemptId(attemptId, pcRetry))
                .thenReturn(Flux.error(new RuntimeException("DAO error")));

        Mono<TrackingsResponse> response = paperTrackerEventService.retrieveTrackingsByAttemptId(attemptId, pcRetry);

        StepVerifier.create(response)
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException
                        && "DAO error".equals(throwable.getMessage()))
                .verify();
        verify(paperTrackingsDAO, times(1)).retrieveEntityByAttemptId(attemptId, pcRetry);
    }

    private TrackingCreationRequest getTrackerCreationRequest() {
        TrackingCreationRequest request = new TrackingCreationRequest();
        request.setAttemptId("request123");
        request.setPcRetry("PCRETRY_0");
        request.setUnifiedDeliveryDriver("driver456");
        request.setProductType("RS");
        return request;
    }

}