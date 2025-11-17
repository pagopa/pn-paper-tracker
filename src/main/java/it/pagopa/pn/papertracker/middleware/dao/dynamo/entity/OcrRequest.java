package it.pagopa.pn.papertracker.middleware.dao.dynamo.entity;

import lombok.Data;
import lombok.Getter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.time.Instant;

@DynamoDbBean
@Data
public class OcrRequest {

    public static final String COL_REQUEST_TIMESTAMP = "requestTimestamp";
    public static final String COL_RESPONSE_TIMESTAMP = "responseTimestamp";
    public static final String COL_DOCUMENT_TYPE = "documentType";
    public static final String COL_EVENT_ID = "eventId";
    public static final String COL_URI = "uri";

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_DOCUMENT_TYPE)}))
    private String documentType;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_EVENT_ID)}))
    private String eventId;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_RESPONSE_TIMESTAMP)}))
    private Instant responseTimestamp;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_REQUEST_TIMESTAMP)}))
    private Instant requestTimestamp;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_URI)}))
    private String uri;
}
