package it.pagopa.pn.papertracker.service.handler_step;

import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.DiscoveredAddress;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.PaperProgressStatusEvent;
import it.pagopa.pn.papertracker.mapper.PaperProgressStatusEventMapper;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.model.HandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetadataUpserterTest {

    @Mock
    private PaperTrackingsDAO paperTrackingsDAO;

    @InjectMocks
    private MetadataUpserter metadataUpserter;

    private HandlerContext handlerContext;
    private PaperProgressStatusEvent paperProgressStatusEvent;
    private PaperTrackings paperTrackings;

    @BeforeEach
    void setUp() {
        paperProgressStatusEvent = new PaperProgressStatusEvent();
        paperProgressStatusEvent.setRequestId("req-123");
        paperProgressStatusEvent.setStatusCode("RECRN002A");

        handlerContext = new HandlerContext();
        handlerContext.setPaperProgressStatusEvent(paperProgressStatusEvent);

        paperTrackings = new PaperTrackings();
        paperTrackings.setRequestId("req-123");
        paperTrackings.setProductType(ProductType.AR);
    }

    @Test
    void execute_WithValidContext_UpdatesItemSuccessfully() {
        // Arrange
        when(paperTrackingsDAO.updateItem(eq("req-123"), any(PaperTrackings.class)))
                .thenReturn(Mono.empty());

        try (MockedStatic<PaperProgressStatusEventMapper> mapperMock =
                     mockStatic(PaperProgressStatusEventMapper.class)) {

            mapperMock.when(() -> PaperProgressStatusEventMapper
                            .createPaperTrackingFromPaperProgressStatusEvent(handlerContext))
                    .thenReturn(Mono.just(paperTrackings));

            // Act & Assert
            StepVerifier.create(metadataUpserter.execute(handlerContext))
                    .verifyComplete();

            verify(paperTrackingsDAO).updateItem("req-123", paperTrackings);
            mapperMock.verify(() -> PaperProgressStatusEventMapper
                    .createPaperTrackingFromPaperProgressStatusEvent(handlerContext));
        }
    }

    @Test
    void execute_WithDiscoveredAddress_SetsAnonimizedDiscoveredAddress() {
        // Arrange
        paperProgressStatusEvent.setDiscoveredAddress(new DiscoveredAddress());

        when(paperTrackingsDAO.updateItem(eq("req-123"), any(PaperTrackings.class)))
                .thenReturn(Mono.empty());

        try (MockedStatic<PaperProgressStatusEventMapper> mapperMock =
                     mockStatic(PaperProgressStatusEventMapper.class)) {

            mapperMock.when(() -> PaperProgressStatusEventMapper
                            .createPaperTrackingFromPaperProgressStatusEvent(any(HandlerContext.class)))
                    .thenReturn(Mono.just(paperTrackings));

            // Act
            StepVerifier.create(metadataUpserter.execute(handlerContext))
                    .verifyComplete();

            // Assert
            assertEquals("", handlerContext.getAnonimizedDiscoveredAddress());
            verify(paperTrackingsDAO).updateItem("req-123", paperTrackings);
        }
    }

    @Test
    void execute_WithNullDiscoveredAddress_DoesNotSetAnonimizedAddress() {
        // Arrange
        paperProgressStatusEvent.setDiscoveredAddress(null);

        when(paperTrackingsDAO.updateItem(eq("req-123"), any(PaperTrackings.class)))
                .thenReturn(Mono.empty());

        try (MockedStatic<PaperProgressStatusEventMapper> mapperMock =
                     mockStatic(PaperProgressStatusEventMapper.class)) {

            mapperMock.when(() -> PaperProgressStatusEventMapper
                            .createPaperTrackingFromPaperProgressStatusEvent(any(HandlerContext.class)))
                    .thenReturn(Mono.just(paperTrackings));

            // Act
            StepVerifier.create(metadataUpserter.execute(handlerContext))
                    .verifyComplete();

            // Assert
            assertNull(handlerContext.getAnonimizedDiscoveredAddress());
            verify(paperTrackingsDAO).updateItem("req-123", paperTrackings);
        }
    }

    @Test
    void execute_WhenMapperFails_PropagatesError() {
        // Arrange
        RuntimeException mapperException = new RuntimeException("Mapper failed");

        try (MockedStatic<PaperProgressStatusEventMapper> mapperMock =
                     mockStatic(PaperProgressStatusEventMapper.class)) {

            mapperMock.when(() -> PaperProgressStatusEventMapper
                            .createPaperTrackingFromPaperProgressStatusEvent(handlerContext))
                    .thenReturn(Mono.error(mapperException));

            // Act & Assert
            StepVerifier.create(metadataUpserter.execute(handlerContext))
                    .expectError(RuntimeException.class)
                    .verify();

            // Verify DAO was never called due to mapper failure
            verify(paperTrackingsDAO, never()).updateItem(any(), any());
        }
    }

    @Test
    void execute_WhenDaoUpdateFails_PropagatesError() {
        // Arrange
        RuntimeException daoException = new RuntimeException("Database update failed");

        when(paperTrackingsDAO.updateItem(eq("req-123"), any(PaperTrackings.class)))
                .thenReturn(Mono.error(daoException));

        try (MockedStatic<PaperProgressStatusEventMapper> mapperMock =
                     mockStatic(PaperProgressStatusEventMapper.class)) {

            mapperMock.when(() -> PaperProgressStatusEventMapper
                            .createPaperTrackingFromPaperProgressStatusEvent(handlerContext))
                    .thenReturn(Mono.just(paperTrackings));

            // Act & Assert
            StepVerifier.create(metadataUpserter.execute(handlerContext))
                    .expectError(RuntimeException.class)
                    .verify();

            verify(paperTrackingsDAO).updateItem("req-123", paperTrackings);
            mapperMock.verify(() -> PaperProgressStatusEventMapper
                    .createPaperTrackingFromPaperProgressStatusEvent(handlerContext));
        }
    }

    @Test
    void execute_WithNullPaperProgressStatusEvent_ThrowsError() {
        // Arrange
        handlerContext.setPaperProgressStatusEvent(null);

        // Act & Assert
        StepVerifier.create(metadataUpserter.execute(handlerContext))
                .expectError(NullPointerException.class)
                .verify();

        // Verify no interactions with dependencies
        verify(paperTrackingsDAO, never()).updateItem(any(), any());
    }

    @Test
    void execute_VerifyExecutionOrder() {
        // Arrange
        paperProgressStatusEvent.setDiscoveredAddress(new DiscoveredAddress());

        when(paperTrackingsDAO.updateItem(eq("req-123"), any(PaperTrackings.class)))
                .thenReturn(Mono.empty());

        try (MockedStatic<PaperProgressStatusEventMapper> mapperMock =
                     mockStatic(PaperProgressStatusEventMapper.class)) {

            mapperMock.when(() -> PaperProgressStatusEventMapper
                            .createPaperTrackingFromPaperProgressStatusEvent(any(HandlerContext.class)))
                    .thenReturn(Mono.just(paperTrackings));

            // Act
            StepVerifier.create(metadataUpserter.execute(handlerContext))
                    .verifyComplete();

            // Assert - verify the flow: anonymization -> mapping -> DAO update
            assertEquals("", handlerContext.getAnonimizedDiscoveredAddress());
            mapperMock.verify(() -> PaperProgressStatusEventMapper
                    .createPaperTrackingFromPaperProgressStatusEvent(handlerContext));
            verify(paperTrackingsDAO).updateItem("req-123", paperTrackings);
        }
    }
}