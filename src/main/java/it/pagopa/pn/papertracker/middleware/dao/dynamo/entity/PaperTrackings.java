package it.pagopa.pn.papertracker.middleware.dao.dynamo.entity;

import lombok.Data;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@DynamoDbBean
@Data
public class PaperTrackings {

    public static final String COL_REQUEST_ID = "requestId";
    public static final String COL_EVENTS = "events";
    public static final String COL_VALIDATION_FLOW = "validationFlow";
    public static final String COL_PRODUCT_TYPE = "productType";
    public static final String COL_OCR_REQUEST_ID = "ocrRequestId";
    public static final String COL_HAS_NEXT_PC_RETRY = "hasNextPcretry";
    public static final String COL_UNIFIED_DELIVERY_DRIVER = "unifiedDeliveryDriver";
    public static final String COL_REGISTERED_LETTER_CODE = "registeredLetterCode";
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

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_UNIFIED_DELIVERY_DRIVER)}))
    private String unifiedDeliveryDriver;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_REGISTERED_LETTER_CODE)}))
    private String registeredLetterCode;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_TTL)}))
    private Long ttl;

    /**
     * Converts a PaperTrackings object to a Map<String, AttributeValue> for DynamoDB update.
     * We need to use dynamoDbAsyncClient.updateItem() to add element to the list of events.
     * When event class is updated, this method should be updated accordingly.
     */
    public static Map<String, AttributeValue> eventToAttributeValueMap(Event event) {
        Map<String, AttributeValue> map = new HashMap<>();
        putIfNotEmpty(map, Event.COL_REQUEST_TIMESTAMP, event.getRequestTimestamp());
        putIfNotEmpty(map, Event.COL_STATUS_CODE, event.getStatusCode());
        putIfNotEmpty(map, Event.COL_STATUS_TIMESTAMP, event.getStatusTimestamp());
        putIfNotNull(map, Event.COL_PRODUCT_TYPE, event.getProductType(), ProductType::getValue);
        putIfNotEmpty(map, Event.COL_DELIVERY_FAILURE_CAUSE, event.getDeliveryFailureCause());
        putIfNotEmpty(map, Event.COL_DISCOVERED_ADDRESS, event.getDiscoveredAddress());
        if (!CollectionUtils.isEmpty(event.getAttachments())) {
            map.put(Event.COL_ATTACHMENTS, AttributeValue.builder().l(event.getAttachments().stream()
                            .map(attachment -> AttributeValue.builder().m(attachmentToAttributeValueMap(attachment)).build())
                            .filter(Objects::nonNull)
                            .toList())
                    .build());
        }
        return map;
    }

    private static Map<String, AttributeValue> attachmentToAttributeValueMap(Attachment attachment) {
        Map<String, AttributeValue> map = new HashMap<>();
        putIfNotEmpty(map, Attachment.COL_ID, attachment.getId());
        putIfNotEmpty(map, Attachment.COL_DOCUMENT_TYPE, attachment.getDocumentType());
        putIfNotEmpty(map, Attachment.COL_URL, attachment.getUrl());
        putIfNotNull(map, Attachment.COL_DATE, attachment.getDate(), Object::toString);
        return map;
    }

    private static void putIfNotEmpty(Map<String, AttributeValue> map, String key, String value) {
        if (StringUtils.isNotEmpty(value)) {
            map.put(key, AttributeValue.builder().s(value).build());
        }
    }

    private static <T> void putIfNotNull(Map<String, AttributeValue> map, String key, T value, Function<T, String> mapper) {
        if (Objects.nonNull(value)) {
            map.put(key, AttributeValue.builder().s(mapper.apply(value)).build());
        }
    }

}
