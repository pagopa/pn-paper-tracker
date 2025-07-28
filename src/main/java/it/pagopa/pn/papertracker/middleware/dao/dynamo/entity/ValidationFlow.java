package it.pagopa.pn.papertracker.middleware.dao.dynamo.entity;

import lombok.Data;
import lombok.Getter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.time.Instant;

@DynamoDbBean
@Data
public class ValidationFlow {

    public static final String COL_OCR_ENABLED = "ocrEnabled";
    public static final String COL_SEQUENCES_VALIDATION_TIMESTAMP = "sequencesValidationTimestamp";
    public static final String COL_OCR_REQUEST_TIMESTAMP = "ocrRequestTimestamp";
    public static final String COL_DEMAT_VALIDATION_TIMESTAMP = "dematValidationTimestamp";

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_OCR_ENABLED)}))
    private Boolean ocrEnabled;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_SEQUENCES_VALIDATION_TIMESTAMP)}))
    private Instant sequencesValidationTimestamp;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_OCR_REQUEST_TIMESTAMP)}))
    private Instant ocrRequestTimestamp;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_DEMAT_VALIDATION_TIMESTAMP)}))
    private Instant dematValidationTimestamp;

}
