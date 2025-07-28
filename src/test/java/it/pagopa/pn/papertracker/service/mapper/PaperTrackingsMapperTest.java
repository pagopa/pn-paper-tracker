package it.pagopa.pn.papertracker.service.mapper;

import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackerCreationRequest;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class PaperTrackingsMapperTest {

    Duration paperTrackingsTtlDuration = Duration.ofDays(3650);

    @Test
    void toPaperTrackingsValidRequest() {
        //ARRANGE
        TrackerCreationRequest request = new TrackerCreationRequest();
        request.setRequestId("request123");
        request.setUnifiedDeliveryDriver("driver456");
        request.setProductType("RS");

        //ACT
        PaperTrackings paperTrackings = PaperTrackingsMapper.toPaperTrackings(request, paperTrackingsTtlDuration);

        //ASSERT
        Assertions.assertEquals("request123", paperTrackings.getRequestId());
        Assertions.assertEquals("driver456", paperTrackings.getUnifiedDeliveryDriver());
        Assertions.assertEquals(ProductType.RS, paperTrackings.getProductType());
        Assertions.assertTrue(paperTrackings.getTtl() > 0);
    }

    @Test
    void toPaperTrackingsInvalidProductType() {
        //ARRANGE
        TrackerCreationRequest request = new TrackerCreationRequest();
        request.setRequestId("request123");
        request.setUnifiedDeliveryDriver("driver456");
        request.setProductType("INVALID_TYPE");

        //ACT & ASSERT
        assertThrows(IllegalArgumentException.class, () -> PaperTrackingsMapper.toPaperTrackings(request, paperTrackingsTtlDuration));
    }

}
