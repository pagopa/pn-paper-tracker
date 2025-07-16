package it.pagopa.pn.papertracker.service.impl;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerConflictException;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackerCreationRequest;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaperTrackerEventServiceImplTest {

    @Mock
    private PaperTrackingsDAO paperTrackingsDAO;

    @Mock
    private PnPaperTrackerConfigs pnPaperTrackerConfigs;

    private PaperTrackerEventServiceImpl paperTrackerEventService;

    @BeforeEach
    void setUp() {
        paperTrackerEventService = new PaperTrackerEventServiceImpl(paperTrackingsDAO, pnPaperTrackerConfigs);
        Mockito.when(pnPaperTrackerConfigs.getPaperTrackingsTtlDuration()).thenReturn(Duration.ofDays(3650));
    }

    @Test
    void insertPaperTrackingsValidRequest() {
        TrackerCreationRequest request = getTrackerCreationRequest();

        when(paperTrackingsDAO.putIfAbsent(argThat(pt ->
                pt.getRequestId().equals(request.getRequestId()) &&
                        pt.getDeliveryDriverId().equals(request.getDeliveryDriverId()) &&
                        pt.getProductType() == ProductType.RS
        ))).thenReturn(Mono.just(new PaperTrackings()));

        Mono<Void> response = paperTrackerEventService.insertPaperTrackings(request);

        StepVerifier.create(response)
                .verifyComplete();
        verify(paperTrackingsDAO, times(1)).putIfAbsent(argThat(pt ->
                pt.getRequestId().equals(request.getRequestId()) &&
                        pt.getDeliveryDriverId().equals(request.getDeliveryDriverId()) &&
                        pt.getProductType() == ProductType.RS
        ));
    }

    @Test
    void insertPaperTrackingsConflictException() {
        TrackerCreationRequest request = getTrackerCreationRequest();

        when(paperTrackingsDAO.putIfAbsent(argThat(pt ->
                pt.getRequestId().equals(request.getRequestId()) &&
                        pt.getDeliveryDriverId().equals(request.getDeliveryDriverId()) &&
                        pt.getProductType() == ProductType.RS
        ))).thenReturn(Mono.error(new PnPaperTrackerConflictException("", "")));

        Mono<Void> response = paperTrackerEventService.insertPaperTrackings(request);

        StepVerifier.create(response)
                .expectError(PnPaperTrackerConflictException.class)
                .verify();
        verify(paperTrackingsDAO, times(1)).putIfAbsent(argThat(pt ->
                pt.getRequestId().equals(request.getRequestId()) &&
                        pt.getDeliveryDriverId().equals(request.getDeliveryDriverId()) &&
                        pt.getProductType() == ProductType.RS
        ));
    }

    private TrackerCreationRequest getTrackerCreationRequest() {
        TrackerCreationRequest request = new TrackerCreationRequest();
        request.setRequestId("request123");
        request.setDeliveryDriverId("driver456");
        request.setProductType("RS");
        return request;
    }

}