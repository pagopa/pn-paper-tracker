package it.pagopa.pn.papertracker.middleware.dao.dynamo.entity;

import lombok.Data;
import lombok.Getter;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@DynamoDbBean
@Data
public class PaperTrackings {

    public static final String COL_REQUEST_ID = "requestId";
    public static final String COL_EVENTS = "events";
    public static final String COL_NOTIFICATION_STATE = "notificationState";
    public static final String COL_VALIDATION_FLOW = "validationFlow";
    public static final String COL_PRODUCT_TYPE = "productType";
    public static final String COL_OCR_REQUEST_ID = "ocrRequestId";
    public static final String COL_HAS_NEXT_PC_RETRY = "hasNextPcretry";
    public static final String COL_UNIFIED_DELIVERY_DRIVER = "unifiedDeliveryDriver";
    public static final String COL_CREATED_AT = "createdAt";
    public static final String COL_UPDATED_AT = "updatedAt";
    public static final String COL_STATE = "state";
    public static final String COL_TTL = "ttl";
    public static final String OCR_REQUEST_ID_INDEX = "ocrRequestId-index";

    @Getter(onMethod = @__({@DynamoDbPartitionKey, @DynamoDbAttribute(COL_REQUEST_ID)}))
    private String requestId;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_EVENTS), @DynamoDbIgnoreNulls}))
    private List<Event> events;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_NOTIFICATION_STATE), @DynamoDbIgnoreNulls}))
    private NotificationState notificationState;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_VALIDATION_FLOW), @DynamoDbIgnoreNulls}))
    private ValidationFlow validationFlow;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_PRODUCT_TYPE)}))
    private ProductType productType;

    @Getter(onMethod = @__({@DynamoDbSecondaryPartitionKey(indexNames = OCR_REQUEST_ID_INDEX), @DynamoDbAttribute(COL_OCR_REQUEST_ID)}))
    private String ocrRequestId;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_HAS_NEXT_PC_RETRY)}))
    private Boolean hasNextPcretry;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_UNIFIED_DELIVERY_DRIVER)}))
    private String unifiedDeliveryDriver;

    @Getter(onMethod = @__({@DynamoDbSortKey, @DynamoDbAttribute(COL_CREATED_AT)}))
    private Instant createdAt;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_UPDATED_AT)}))
    private Instant updatedAt;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_STATE)}))
    private PaperTrackingsState state;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_TTL)}))
    private Long ttl;

    /**
     * Converte l'entity PaperTrackings in una mappa {@code Map<String, AttributeValue>} utilizzata da DynamoDB.
     */
    public static Map<String, AttributeValue> paperTrackingsToAttributeValueMap(PaperTrackings paperTrackings) {
        var schema = TableSchema.fromBean(PaperTrackings.class);
        return schema.itemToMap(paperTrackings, true);
    }

    /**
     * Converte una mappa {@code Map<String, AttributeValue>} utilizzata da DynamoDB in una entity PaperTrackings.
     */
    public static PaperTrackings attributeValueMapToPaperTrackings(Map<String, AttributeValue> item) {
        var schema = TableSchema.fromBean(PaperTrackings.class);
        return schema.mapToItem(item);
    }

}
