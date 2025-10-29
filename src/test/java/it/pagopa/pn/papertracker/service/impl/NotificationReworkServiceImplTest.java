package it.pagopa.pn.papertracker.service.impl;

import it.pagopa.pn.papertracker.config.SequenceConfiguration;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerBadRequestException;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.SequenceResponse;
import it.pagopa.pn.papertracker.model.DocumentTypeEnum;
import it.pagopa.pn.papertracker.model.SequenceElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class NotificationReworkServiceImplTest {

    @InjectMocks
    private NotificationReworkServiceImpl service;

    @Test
    void notificationRework_shouldReturnSequenceForValidStatusCode() {
        String statusCode = "RECRN005C";
        String deliveryFailureCause = "M02";

        Set<SequenceElement> list = SequenceConfiguration.SequenceDefinition.fromKey(statusCode).getSequence();
        StepVerifier.create(service.notificationRework(statusCode, deliveryFailureCause))
                .assertNext(response -> {
                    assertEquals(SequenceResponse.FinalStatusCodeEnum.OK, response.getFinalStatusCode());
                    assertNotNull(response.getSequence());
                    assertEquals(
                            list.size(),
                            response.getSequence().size()
                    );
                    assertEquals(
                            list.stream().filter(sequenceElement -> !CollectionUtils.isEmpty(sequenceElement.getRequiredDocumentType())).toList().size(),
                            response.getSequence().stream().filter(sequenceItem -> !CollectionUtils.isEmpty(sequenceItem.getAttachments())).toList().size()
                    );
                    response.getSequence().forEach(sequenceItem -> {
                        if(!CollectionUtils.isEmpty(sequenceItem.getAttachments())) {
                            assertTrue(sequenceItem.getAttachments().containsAll(list.stream()
                                    .filter(sequenceElement -> sequenceElement.getCode().equalsIgnoreCase(sequenceItem.getStatusCode()))
                                    .flatMap(sequenceElement -> sequenceElement.getRequiredDocumentType().stream().map(DocumentTypeEnum::getValue))
                                    .toList()));
                        }
                    });
                    response.getSequence().forEach(item -> {
                        assertTrue(StringUtils.hasText(item.getStatusCode()));

                    });
                })
                .verifyComplete();
    }

    @Test
    void notificationRework_shouldReturnSequenceForKOStatusCode() {
        String statusCode = "RECRN002F";
        String deliveryFailureCause = "M02";
        Set<SequenceElement> list = SequenceConfiguration.SequenceDefinition.fromKey(statusCode).getSequence();

        StepVerifier.create(service.notificationRework(statusCode, deliveryFailureCause))
                .assertNext(response -> {
                    assertEquals(SequenceResponse.FinalStatusCodeEnum.KO, response.getFinalStatusCode());
                    assertNotNull(response.getSequence());
                    assertEquals(
                            SequenceConfiguration.SequenceDefinition.fromKey(statusCode).getSequence().size(),
                            response.getSequence().size()
                    );
                    response.getSequence().forEach(item -> {
                        assertTrue(StringUtils.hasText(item.getStatusCode()));
                    });
                    response.getSequence().forEach(sequenceItem -> {
                        if(!CollectionUtils.isEmpty(sequenceItem.getAttachments())) {
                            assertTrue(sequenceItem.getAttachments().containsAll(list.stream()
                                    .filter(sequenceElement -> sequenceElement.getCode().equalsIgnoreCase(sequenceItem.getStatusCode()))
                                    .flatMap(sequenceElement -> sequenceElement.getRequiredDocumentType().stream().map(DocumentTypeEnum::getValue))
                                    .toList()));
                        }
                    });
                })
                .verifyComplete();
    }

    @Test
    void notificationRework_shouldReturnOKForRECRN002CWithM02() {
        String statusCode = "RECRN002C";
        String deliveryFailureCause = "M02";
        Set<SequenceElement> list = SequenceConfiguration.SequenceDefinition.fromKey(statusCode).getSequence();

        StepVerifier.create(service.notificationRework(statusCode, deliveryFailureCause))
                .assertNext(response -> {
                    assertEquals(SequenceResponse.FinalStatusCodeEnum.OK, response.getFinalStatusCode());
                    assertNotNull(response.getSequence());
                    assertEquals(
                            SequenceConfiguration.SequenceDefinition.fromKey(statusCode).getSequence().size(),
                            response.getSequence().size()
                    );
                    assertEquals(
                            list.stream().filter(sequenceElement -> !CollectionUtils.isEmpty(sequenceElement.getRequiredDocumentType())).toList().size(),
                            response.getSequence().stream().filter(sequenceItem -> !CollectionUtils.isEmpty(sequenceItem.getAttachments())).toList().size()
                    );
                    response.getSequence().forEach(item -> {
                        assertTrue(StringUtils.hasText(item.getStatusCode()));
                    });
                    response.getSequence().forEach(sequenceItem -> {
                        if(!CollectionUtils.isEmpty(sequenceItem.getAttachments())) {
                            assertTrue(sequenceItem.getAttachments().containsAll(list.stream()
                                    .filter(sequenceElement -> sequenceElement.getCode().equalsIgnoreCase(sequenceItem.getStatusCode()))
                                    .flatMap(sequenceElement -> sequenceElement.getRequiredDocumentType().stream().map(DocumentTypeEnum::getValue))
                                    .toList()));
                        }
                    });
                })
                .verifyComplete();
    }

    @Test
    void notificationRework_shouldReturnOKForRECRN002CWithM05() {
        String statusCode = "RECRN002C";
        String deliveryFailureCause = "M05";
        Set<SequenceElement> list = SequenceConfiguration.SequenceDefinition.fromKey(statusCode).getSequence();

        StepVerifier.create(service.notificationRework(statusCode, deliveryFailureCause))
                .assertNext(response -> {
                    assertEquals(SequenceResponse.FinalStatusCodeEnum.OK, response.getFinalStatusCode());
                    assertNotNull(response.getSequence());
                    assertEquals(
                            SequenceConfiguration.SequenceDefinition.fromKey(statusCode).getSequence().size(),
                            response.getSequence().size()
                    );
                    assertEquals(
                            list.stream().filter(sequenceElement -> !CollectionUtils.isEmpty(sequenceElement.getRequiredDocumentType())).toList().size(),
                            response.getSequence().stream().filter(sequenceItem -> !CollectionUtils.isEmpty(sequenceItem.getAttachments())).toList().size()
                    );
                    response.getSequence().forEach(item -> {
                        assertTrue(StringUtils.hasText(item.getStatusCode()));
                    });
                    response.getSequence().forEach(sequenceItem -> {
                        if(!CollectionUtils.isEmpty(sequenceItem.getAttachments())) {
                            assertTrue(sequenceItem.getAttachments().containsAll(list.stream()
                                    .filter(sequenceElement -> sequenceElement.getCode().equalsIgnoreCase(sequenceItem.getStatusCode()))
                                    .flatMap(sequenceElement -> sequenceElement.getRequiredDocumentType().stream().map(DocumentTypeEnum::getValue))
                                    .toList()));
                        }
                    });
                })
                .verifyComplete();
    }

    @Test
    void notificationRework_shouldReturnKOForRECRN002CWithM06() {
        String statusCode = "RECRN002C";
        String deliveryFailureCause = "M06";
        Set<SequenceElement> list = SequenceConfiguration.SequenceDefinition.fromKey(statusCode).getSequence();

        StepVerifier.create(service.notificationRework(statusCode, deliveryFailureCause))
                .assertNext(response -> {
                    assertEquals(SequenceResponse.FinalStatusCodeEnum.KO, response.getFinalStatusCode());
                    assertNotNull(response.getSequence());
                    assertEquals(
                            SequenceConfiguration.SequenceDefinition.fromKey(statusCode).getSequence().size(),
                            response.getSequence().size()
                    );
                    assertEquals(
                            list.stream().filter(sequenceElement -> !CollectionUtils.isEmpty(sequenceElement.getRequiredDocumentType())).toList().size(),
                            response.getSequence().stream().filter(sequenceItem -> !CollectionUtils.isEmpty(sequenceItem.getAttachments())).toList().size()
                    );
                    response.getSequence().forEach(sequenceItem -> {
                        if(!CollectionUtils.isEmpty(sequenceItem.getAttachments())) {
                            assertTrue(sequenceItem.getAttachments().containsAll(list.stream()
                                    .filter(sequenceElement -> sequenceElement.getCode().equalsIgnoreCase(sequenceItem.getStatusCode()))
                                    .flatMap(sequenceElement -> sequenceElement.getRequiredDocumentType().stream().map(DocumentTypeEnum::getValue))
                                    .toList()));
                        }
                    });
                    response.getSequence().forEach(item -> {
                        assertTrue(StringUtils.hasText(item.getStatusCode()));
                    });
                })
                .verifyComplete();
    }

    @Test
    void notificationRework_shouldReturnKOForRECRN002CWithM07() {
        String statusCode = "RECRN002C";
        String deliveryFailureCause = "M07";
        Set<SequenceElement> list = SequenceConfiguration.SequenceDefinition.fromKey(statusCode).getSequence();

        StepVerifier.create(service.notificationRework(statusCode, deliveryFailureCause))
                .assertNext(response -> {
                    assertEquals(SequenceResponse.FinalStatusCodeEnum.KO, response.getFinalStatusCode());
                    assertNotNull(response.getSequence());
                    assertEquals(
                            SequenceConfiguration.SequenceDefinition.fromKey(statusCode).getSequence().size(),
                            response.getSequence().size()
                    );
                    assertEquals(
                            list.stream().filter(sequenceElement -> !CollectionUtils.isEmpty(sequenceElement.getRequiredDocumentType())).toList().size(),
                            response.getSequence().stream().filter(sequenceItem -> !CollectionUtils.isEmpty(sequenceItem.getAttachments())).toList().size()
                    );
                    response.getSequence().forEach(item -> {
                        assertTrue(StringUtils.hasText(item.getStatusCode()));
                    });
                    response.getSequence().forEach(sequenceItem -> {
                        if(!CollectionUtils.isEmpty(sequenceItem.getAttachments())) {
                            assertTrue(sequenceItem.getAttachments().containsAll(list.stream()
                                    .filter(sequenceElement -> sequenceElement.getCode().equalsIgnoreCase(sequenceItem.getStatusCode()))
                                    .flatMap(sequenceElement -> sequenceElement.getRequiredDocumentType().stream().map(DocumentTypeEnum::getValue))
                                    .toList()));
                        }
                    });
                })
                .verifyComplete();
    }

    @Test
    void notificationRework_shouldReturnKOForRECRN002CWithM08() {
        String statusCode = "RECRN002C";
        String deliveryFailureCause = "M08";
        Set<SequenceElement> list = SequenceConfiguration.SequenceDefinition.fromKey(statusCode).getSequence();

        StepVerifier.create(service.notificationRework(statusCode, deliveryFailureCause))
                .assertNext(response -> {
                    assertEquals(SequenceResponse.FinalStatusCodeEnum.KO, response.getFinalStatusCode());
                    assertNotNull(response.getSequence());
                    assertEquals(
                            SequenceConfiguration.SequenceDefinition.fromKey(statusCode).getSequence().size(),
                            response.getSequence().size()
                    );
                    assertEquals(
                            list.stream().filter(sequenceElement -> !CollectionUtils.isEmpty(sequenceElement.getRequiredDocumentType())).toList().size(),
                            response.getSequence().stream().filter(sequenceItem -> !CollectionUtils.isEmpty(sequenceItem.getAttachments())).toList().size()
                    );
                    response.getSequence().forEach(item -> {
                        assertTrue(StringUtils.hasText(item.getStatusCode()));
                    });
                    response.getSequence().forEach(sequenceItem -> {
                        if(!CollectionUtils.isEmpty(sequenceItem.getAttachments())) {
                            assertTrue(sequenceItem.getAttachments().containsAll(list.stream()
                                    .filter(sequenceElement -> sequenceElement.getCode().equalsIgnoreCase(sequenceItem.getStatusCode()))
                                    .flatMap(sequenceElement -> sequenceElement.getRequiredDocumentType().stream().map(DocumentTypeEnum::getValue))
                                    .toList()));
                        }
                    });
                })
                .verifyComplete();
    }

    @Test
    void notificationRework_shouldReturnKOForRECRN002CWithM09() {
        String statusCode = "RECRN002C";
        String deliveryFailureCause = "M09";
        Set<SequenceElement> list = SequenceConfiguration.SequenceDefinition.fromKey(statusCode).getSequence();

        StepVerifier.create(service.notificationRework(statusCode, deliveryFailureCause))
                .assertNext(response -> {
                    assertEquals(SequenceResponse.FinalStatusCodeEnum.KO, response.getFinalStatusCode());
                    assertNotNull(response.getSequence());
                    assertFalse(response.getSequence().isEmpty());
                    assertEquals(
                            SequenceConfiguration.SequenceDefinition.fromKey(statusCode).getSequence().size(),
                            response.getSequence().size()
                    );
                    assertEquals(
                            list.stream().filter(sequenceElement -> !CollectionUtils.isEmpty(sequenceElement.getRequiredDocumentType())).toList().size(),
                            response.getSequence().stream().filter(sequenceItem -> !CollectionUtils.isEmpty(sequenceItem.getAttachments())).toList().size()
                    );
                    response.getSequence().forEach(item -> {
                        assertTrue(StringUtils.hasText(item.getStatusCode()));
                    });
                    response.getSequence().forEach(sequenceItem -> {
                        if(!CollectionUtils.isEmpty(sequenceItem.getAttachments())) {
                            assertTrue(sequenceItem.getAttachments().containsAll(list.stream()
                                    .filter(sequenceElement -> sequenceElement.getCode().equalsIgnoreCase(sequenceItem.getStatusCode()))
                                    .flatMap(sequenceElement -> sequenceElement.getRequiredDocumentType().stream().map(DocumentTypeEnum::getValue))
                                    .toList()));
                        }
                    });
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
        Set<SequenceElement> list = SequenceConfiguration.SequenceDefinition.fromKey(statusCode).getSequence();

        StepVerifier.create(service.notificationRework(statusCode, deliveryFailureCause))
                .assertNext(response -> {
                    assertNotNull(response.getSequence());
                    response.getSequence().forEach(item -> {
                        assertTrue(StringUtils.hasText(item.getStatusCode()));
                    });
                    assertEquals(
                            list.stream().filter(sequenceElement -> !CollectionUtils.isEmpty(sequenceElement.getRequiredDocumentType())).toList().size(),
                            response.getSequence().stream().filter(sequenceItem -> !CollectionUtils.isEmpty(sequenceItem.getAttachments())).toList().size()
                    );
                    assertEquals(list.size(), response.getSequence().size());
                    response.getSequence().forEach(sequenceItem -> {
                        if(!CollectionUtils.isEmpty(sequenceItem.getAttachments())) {
                            assertTrue(sequenceItem.getAttachments().containsAll(list.stream()
                                    .filter(sequenceElement -> sequenceElement.getCode().equalsIgnoreCase(sequenceItem.getStatusCode()))
                                    .flatMap(sequenceElement -> sequenceElement.getRequiredDocumentType().stream().map(DocumentTypeEnum::getValue))
                                    .toList()));
                        }
                    });
                })
                .verifyComplete();
    }

    @Test
    void notificationRework_shouldThrowExceptionForInvalidStatusCode() {
        String invalidStatusCode = "INVALID";
        String deliveryFailureCause = "M02";

        StepVerifier.create(service.notificationRework(invalidStatusCode, deliveryFailureCause))
                .expectErrorMatches(throwable -> {
                    if (throwable instanceof PnPaperTrackerBadRequestException ex) {
                        return ex.getProblem().getDetail().contains("statusCode INVALID is invalid");
                    }
                    return false;
                })
                .verify();
    }

    @Test
    void notificationRework_shouldThrowExceptionForEmptyStatusCode() {
        String emptyStatusCode = "";
        String deliveryFailureCause = "M02";

        StepVerifier.create(service.notificationRework(emptyStatusCode, deliveryFailureCause))
                .expectErrorMatches(throwable -> {
                    if (throwable instanceof PnPaperTrackerBadRequestException ex) {
                        return ex.getProblem().getDetail().contains("is invalid");
                    }
                    return false;
                })
                .verify();
    }
}