package it.pagopa.pn.papertracker.service.impl;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerConflictException;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingCreationRequest;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsErrorsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsErrors;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaperTrackerEventServiceImplTest {

    @Mock
    private PaperTrackingsDAO paperTrackingsDAO;

    @Mock
    private PaperTrackingsErrorsDAO paperTrackingsErrorsDAO;

    @Mock
    private PnPaperTrackerConfigs pnPaperTrackerConfigs;

    private PaperTrackerEventServiceImpl paperTrackerEventService;

    @BeforeEach
    void setUp() {
        paperTrackerEventService = new PaperTrackerEventServiceImpl(paperTrackingsDAO, paperTrackingsErrorsDAO, pnPaperTrackerConfigs);
    }

    @Test
    void insertPaperTrackingsValidRequest() {
        //ARRANGE
        TrackingCreationRequest request = getTrackerCreationRequest();

        when(paperTrackingsDAO.putIfAbsent(argThat(pt ->
                pt.getTrackingId().equals(request.getTrackingId()) &&
                        pt.getUnifiedDeliveryDriver().equals(request.getUnifiedDeliveryDriver()) &&
                        pt.getProductType() == ProductType.RS
        ))).thenReturn(Mono.just(new PaperTrackings()));

        //ACT
        Mono<Void> response = paperTrackerEventService.insertPaperTrackings(request);

        //ASSERT
        StepVerifier.create(response)
                .verifyComplete();
        verify(paperTrackingsDAO, times(1)).putIfAbsent(argThat(pt ->
                pt.getTrackingId().equals(request.getTrackingId()) &&
                        pt.getUnifiedDeliveryDriver().equals(request.getUnifiedDeliveryDriver()) &&
                        pt.getProductType() == ProductType.RS
        ));
    }

    @Test
    void insertPaperTrackingsConflictException() {
        //ARRANGE
        TrackingCreationRequest request = getTrackerCreationRequest();

        when(paperTrackingsDAO.putIfAbsent(argThat(pt ->
                pt.getTrackingId().equals(request.getTrackingId()) &&
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
                pt.getTrackingId().equals(request.getTrackingId()) &&
                        pt.getUnifiedDeliveryDriver().equals(request.getUnifiedDeliveryDriver()) &&
                        pt.getProductType() == ProductType.RS
        ));
    }

    @Test
    void insertPaperTrackingsErrorsSuccessfully() {
        //ARRANGE
        PaperTrackingsErrors paperTrackingsErrors = new PaperTrackingsErrors();
        when(paperTrackingsErrorsDAO.insertError(paperTrackingsErrors)).thenReturn(Mono.just(paperTrackingsErrors));

        //ACT
        Mono<Void> response = paperTrackerEventService.insertPaperTrackingsErrors(paperTrackingsErrors);

        //ASSERT
        StepVerifier.create(response)
                .verifyComplete();
        verify(paperTrackingsErrorsDAO, times(1)).insertError(paperTrackingsErrors);
    }

    private TrackingCreationRequest getTrackerCreationRequest() {
        TrackingCreationRequest request = new TrackingCreationRequest();
        request.setTrackingId("request123");
        request.setUnifiedDeliveryDriver("driver456");
        request.setProductType("RS");
        return request;
    }

}