package it.pagopa.pn.papertracker.middleware.dao.dynamo.entity;

import lombok.Data;
import lombok.Getter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

import java.util.List;

@DynamoDbBean
@Data
public class PaperTrackingsEntity {

    public static final String COL_REQUEST_ID = "requestId";
    public static final String COL_EVENTS = "events";
    public static final String COL_VALIDATION_FLOW = "validationFlow";
    public static final String COL_PRODUCT_TYPE = "productType";
    public static final String COL_OCR_REQUEST_ID = "ocrRequestId";
    public static final String COL_HAS_NEXT_PC_RETRY = "hasNextPcretry";
    public static final String COL_DELIVERY_DRIVER_ID = "deliveryDriverId";
    public static final String COL_TTL = "ttl";
    public static final String OCR_REQUEST_ID_INDEX = "ocrRequestId-index";

    @Getter(onMethod = @__({@DynamoDbPartitionKey, @DynamoDbAttribute(COL_REQUEST_ID)}))
    private String requestId;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_EVENTS)}))
    private List<Event> events;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_VALIDATION_FLOW)}))
    private ValidationFlow validationFlow;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_PRODUCT_TYPE)}))
    private ProductType productType;

    @Getter(onMethod = @__({@DynamoDbSecondaryPartitionKey(indexNames = OCR_REQUEST_ID_INDEX), @DynamoDbAttribute(COL_OCR_REQUEST_ID)}))
    private String ocrRequestId;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_HAS_NEXT_PC_RETRY)}))
    private Boolean hasNextPcretry;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_DELIVERY_DRIVER_ID)}))
    private String deliveryDriverId;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_TTL)}))
    private Long ttl;

}
