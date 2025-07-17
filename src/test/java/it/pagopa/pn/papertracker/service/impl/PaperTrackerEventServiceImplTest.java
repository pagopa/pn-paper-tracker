package it.pagopa.pn.papertracker.service.impl;

import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsErrorsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsErrors;
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
    private PaperTrackingsErrorsDAO paperTrackingsErrorsDAO;

    private PaperTrackerEventServiceImpl paperTrackerEventService;

    @BeforeEach
    void setUp() {
        paperTrackerEventService = new PaperTrackerEventServiceImpl(paperTrackingsErrorsDAO);
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

}