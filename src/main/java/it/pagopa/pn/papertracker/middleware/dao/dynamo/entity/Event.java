package it.pagopa.pn.papertracker.middleware.dao.dynamo.entity;

import lombok.Data;
import lombok.Getter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.time.Instant;
import java.util.List;

@DynamoDbBean
@Data
public class Event {

    public static final String COL_REQUEST_TIMESTAMP = "requestTimestamp";
    public static final String COL_STATUS_CODE = "statusCode";
    public static final String COL_STATUS_TIMESTAMP = "statusTimestamp";
    public static final String COL_PRODUCT_TYPE = "productType";
    public static final String COL_DELIVERY_FAILURE_CAUSE = "deliveryFailureCause";
    public static final String COL_DISCOVERED_ADDRESS = "discoveredAddress";
    public static final String COL_ATTACHMENTS = "attachments";
    public static final String COL_REGISTERED_LETTER_CODE = "registeredLetterCode";

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_REQUEST_TIMESTAMP)}))
    private Instant requestTimestamp;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_STATUS_CODE)}))
    private String statusCode;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_STATUS_TIMESTAMP)}))
    private Instant statusTimestamp;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_PRODUCT_TYPE)}))
    private ProductType productType;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_DELIVERY_FAILURE_CAUSE)}))
    private String deliveryFailureCause;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_DISCOVERED_ADDRESS)}))
    private String discoveredAddress;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_ATTACHMENTS)}))
    private List<Attachment> attachments;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_REGISTERED_LETTER_CODE)}))
    private String registeredLetterCode;

}
