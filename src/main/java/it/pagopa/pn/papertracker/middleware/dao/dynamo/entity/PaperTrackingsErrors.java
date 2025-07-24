package it.pagopa.pn.papertracker.middleware.dao.dynamo.entity;

import lombok.Data;
import lombok.Getter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.time.Instant;

@DynamoDbBean
@Data
public class PaperTrackingsErrors {

    public static final String COL_REQUEST_ID = "requestId";
    public static final String COL_CREATED = "created";
    public static final String COL_CATEGORY = "category";
    public static final String COL_DETAILS = "details";
    public static final String COL_FLOW_THROW = "flowThrow";
    public static final String COL_EVENT_THROW = "eventThrow";
    public static final String COL_PRODUCT_TYPE = "productType";
    public static final String COL_TYPE = "type";

    @Getter(onMethod = @__({@DynamoDbPartitionKey, @DynamoDbAttribute(COL_REQUEST_ID)}))
    private String requestId;

    @Getter(onMethod = @__({@DynamoDbSortKey, @DynamoDbAttribute(COL_CREATED)}))
    private Instant created;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_CATEGORY)}))
    private ErrorCategory errorCategory;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_DETAILS)}))
    private ErrorDetails details;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_FLOW_THROW)}))
    private FlowThrow flowThrow;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_EVENT_THROW)}))
    private String eventThrow;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_PRODUCT_TYPE)}))
    private ProductType productType;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_TYPE)}))
    private ErrorType type;

}
