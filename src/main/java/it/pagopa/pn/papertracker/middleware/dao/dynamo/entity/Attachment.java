package it.pagopa.pn.papertracker.middleware.dao.dynamo.entity;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.time.Instant;

@DynamoDbBean
@Data
@NoArgsConstructor
public class Attachment {

    public static final String COL_ID = "id";
    public static final String COL_DOCUMENT_TYPE = "documentType";
    public static final String COL_URL = "url";
    public static final String COL_DATE = "date";

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_ID)}))
    private String id;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_DOCUMENT_TYPE)}))
    private String documentType;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_URL)}))
    private String url;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_DATE)}))
    private Instant date;

}