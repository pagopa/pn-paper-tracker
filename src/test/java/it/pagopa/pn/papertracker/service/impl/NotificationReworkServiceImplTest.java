package it.pagopa.pn.papertracker.service.impl;

import it.pagopa.pn.papertracker.config.SequenceConfiguration;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerBadRequestException;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.SequenceResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class NotificationReworkServiceImplTest {

    @InjectMocks
    private NotificationReworkServiceImpl service;

    @Test
    void notificationRework_shouldReturnSequenceForValidStatusCode() {
        String statusCode = "RECRN005C";
        String deliveryFailureCause = "M02";

        StepVerifier.create(service.notificationRework(statusCode, deliveryFailureCause))
                .assertNext(response -> {
                    assertEquals(SequenceResponse.FinalStatusCodeEnum.OK, response.getFinalStatusCode());
                    assertNotNull(response.getSequence());
                    assertFalse(response.getSequence().isEmpty());
                    assertEquals(
                            SequenceConfiguration.SequenceDefinition.fromKey(statusCode).getSequence().size(),
                            response.getSequence().size()
                    );
                })
                .verifyComplete();
    }

    @Test
    void notificationRework_shouldReturnSequenceForKOStatusCode() {
        String statusCode = "RECRN002F";
        String deliveryFailureCause = "M02";

        StepVerifier.create(service.notificationRework(statusCode, deliveryFailureCause))
                .assertNext(response -> {
                    assertEquals(SequenceResponse.FinalStatusCodeEnum.KO, response.getFinalStatusCode());
                    assertNotNull(response.getSequence());
                    assertFalse(response.getSequence().isEmpty());
                    assertEquals(
                            SequenceConfiguration.SequenceDefinition.fromKey(statusCode).getSequence().size(),
                            response.getSequence().size()
                    );
                })
                .verifyComplete();
    }

    @Test
    void notificationRework_shouldReturnOKForRECRN002CWithM02() {
        String statusCode = "RECRN002C";
        String deliveryFailureCause = "M02";

        StepVerifier.create(service.notificationRework(statusCode, deliveryFailureCause))
                .assertNext(response -> {
                    assertEquals(SequenceResponse.FinalStatusCodeEnum.OK, response.getFinalStatusCode());
                    assertNotNull(response.getSequence());
                    assertFalse(response.getSequence().isEmpty());
                })
                .verifyComplete();
    }

    @Test
    void notificationRework_shouldReturnOKForRECRN002CWithM05() {
        String statusCode = "RECRN002C";
        String deliveryFailureCause = "M05";

        StepVerifier.create(service.notificationRework(statusCode, deliveryFailureCause))
                .assertNext(response -> {
                    assertEquals(SequenceResponse.FinalStatusCodeEnum.OK, response.getFinalStatusCode());
                    assertNotNull(response.getSequence());
                    assertFalse(response.getSequence().isEmpty());
                })
                .verifyComplete();
    }

    @Test
    void notificationRework_shouldReturnKOForRECRN002CWithM06() {
        String statusCode = "RECRN002C";
        String deliveryFailureCause = "M06";

        StepVerifier.create(service.notificationRework(statusCode, deliveryFailureCause))
                .assertNext(response -> {
                    assertEquals(SequenceResponse.FinalStatusCodeEnum.KO, response.getFinalStatusCode());
                    assertNotNull(response.getSequence());
                    assertFalse(response.getSequence().isEmpty());
                })
                .verifyComplete();
    }

    @Test
    void notificationRework_shouldReturnKOForRECRN002CWithM07() {
        String statusCode = "RECRN002C";
        String deliveryFailureCause = "M07";

        StepVerifier.create(service.notificationRework(statusCode, deliveryFailureCause))
                .assertNext(response -> {
                    assertEquals(SequenceResponse.FinalStatusCodeEnum.KO, response.getFinalStatusCode());
                    assertNotNull(response.getSequence());
                    assertFalse(response.getSequence().isEmpty());
                })
                .verifyComplete();
    }

    @Test
    void notificationRework_shouldReturnKOForRECRN002CWithM08() {
        String statusCode = "RECRN002C";
        String deliveryFailureCause = "M08";

        StepVerifier.create(service.notificationRework(statusCode, deliveryFailureCause))
                .assertNext(response -> {
                    assertEquals(SequenceResponse.FinalStatusCodeEnum.KO, response.getFinalStatusCode());
                    assertNotNull(response.getSequence());
                    assertFalse(response.getSequence().isEmpty());
                })
                .verifyComplete();
    }

    @Test
    void notificationRework_shouldReturnKOForRECRN002CWithM09() {
        String statusCode = "RECRN002C";
        String deliveryFailureCause = "M09";

        StepVerifier.create(service.notificationRework(statusCode, deliveryFailureCause))
                .assertNext(response -> {
                    assertEquals(SequenceResponse.FinalStatusCodeEnum.KO, response.getFinalStatusCode());
                    assertNotNull(response.getSequence());
                    assertFalse(response.getSequence().isEmpty());
                })
                .verifyComplete();
    }

    @Test
    void notificationRework_shouldThrowExceptionForProgressStatusCode() {
        String progressStatusCode = "RECRN010";
        String deliveryFailureCause = "M02";

        StepVerifier.create(service.notificationRework(progressStatusCode, deliveryFailureCause))
                .expectErrorMatches(throwable -> {
                    if (throwable instanceof PnPaperTrackerBadRequestException ex) {
                        return ex.getProblem().getDetail().contains("statusCode RECRN010 is PROGRESS");
                    }
                    return false;
                })
                .verify();
    }

    @Test
    void notificationRework_shouldVerifySequenceContent() {
        String statusCode = "RECRN005C";
        String deliveryFailureCause = "M02";

        StepVerifier.create(service.notificationRework(statusCode, deliveryFailureCause))
                .assertNext(response -> {
                    assertNotNull(response.getSequence());
                    response.getSequence().forEach(code -> {
                        assertNotNull(code);
                        assertFalse(code.isEmpty());
                    });
                    SequenceConfiguration.SequenceDefinition definition =
                            SequenceConfiguration.SequenceDefinition.fromKey(statusCode);
                    assertEquals(definition.getSequence().size(), response.getSequence().size());
                })
                .verifyComplete();
    }
}