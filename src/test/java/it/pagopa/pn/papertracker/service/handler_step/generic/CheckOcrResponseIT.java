package it.pagopa.pn.papertracker.service.handler_step.generic;

import com.sngular.apigenerator.asyncapi.business_model.model.event.Data;
import com.sngular.apigenerator.asyncapi.business_model.model.event.OcrDataResultPayload;
import it.pagopa.pn.papertracker.BaseTest;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsErrorsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.*;
import it.pagopa.pn.papertracker.middleware.queue.consumer.internal.OcrEventHandler;
import it.pagopa.pn.papertracker.model.DocumentTypeEnum;
import it.pagopa.pn.papertracker.model.OcrStatusEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CheckOcrResponseIT extends BaseTest.WithLocalStack {

    @Autowired
    OcrEventHandler ocrEventHandler;

    @Autowired
    PaperTrackingsDAO paperTrackingsDAO;

    @Autowired
    PaperTrackingsErrorsDAO paperTrackingsErrorsDAO;

    @Test
    void checkOcrResponseHandler(){
        OcrDataResultPayload payload = OcrDataResultPayload.builder()
                .CommandId("PREPARE#id1#TEST")
                .data(Data.builder().description("description").predictedRefinementType(Data.PredictedRefinementType.POST10)
                        .validationType(Data.ValidationType.AI).validationStatus(Data.ValidationStatus.KO).build())
                .version("version")
                .build();

        String requestId = "PREPARE";
        PaperTrackings paperTrackings = new PaperTrackings();
        paperTrackings.setTrackingId(requestId);
        paperTrackings.setProductType(ProductType.AR.getValue());
        paperTrackings.setUnifiedDeliveryDriver("POSTE");
        paperTrackings.setState(PaperTrackingsState.AWAITING_REFINEMENT);
        ValidationConfig validationConfig = new ValidationConfig();
        validationConfig.setOcrEnabled(OcrStatusEnum.DRY);
        paperTrackings.setValidationConfig(validationConfig);
        Instant timestamp = Instant.now();
        Event event1 = buildEvent("id1","RECRN001A", timestamp, timestamp, "REG123", "", null);
        Event event2 = buildEvent("id2", "RECRN001B", timestamp, timestamp.plusSeconds(1), "REG123", "", List.of(DocumentTypeEnum.AR.getValue()));
        Event event3 = buildEvent("id3", "RECRN001C", timestamp, timestamp.plusSeconds(2), "REG123", "", null);


        paperTrackings.setEvents(List.of(event1, event2,event3));

        paperTrackingsDAO.putIfAbsent(paperTrackings).block();

        Assertions.assertDoesNotThrow(()-> ocrEventHandler.handleOcrMessage(payload));
        PaperTrackingsErrors paperTrackingsErrors = paperTrackingsErrorsDAO.retrieveErrors(paperTrackings.getTrackingId()).blockFirst();
        Assertions.assertNotNull(paperTrackingsErrors);
        Object additionalDetailsField = paperTrackingsErrors.getDetails().getAdditionalDetails().get("ocrDataResultPayload");
        if (additionalDetailsField instanceof HashMap<?,?> map) {
            Assertions.assertEquals("description", map.get("description"));
            Assertions.assertEquals("POST10", map.get("predictedRefinementType"));
            Assertions.assertEquals("ai", map.get("validationType"));
            Assertions.assertEquals("KO", map.get("validationStatus"));
        } else {
            Assertions.fail("Expected additionalDetailsField to be a Map");
        }
    }

    private Event buildEvent(String id, String statusCode, Instant statusTimestamp, Instant requestTimestamp, String registeredLetterCode, String deliveryFailureCause, List<String> attachmentTypes) {
        Event event = new Event();
        event.setId(id);
        event.setAttachments(new ArrayList<>());
        event.setStatusCode(statusCode);
        event.setRegisteredLetterCode(registeredLetterCode);
        event.setStatusTimestamp(statusTimestamp);
        event.setRequestTimestamp(requestTimestamp);
        event.setDeliveryFailureCause(deliveryFailureCause);

        if (!CollectionUtils.isEmpty(attachmentTypes)) {
            for (String type : attachmentTypes) {
                Attachment attachment = new Attachment();
                attachment.setDocumentType(type);
                event.getAttachments().add(attachment);
            }
        }

        return event;
    }
}
