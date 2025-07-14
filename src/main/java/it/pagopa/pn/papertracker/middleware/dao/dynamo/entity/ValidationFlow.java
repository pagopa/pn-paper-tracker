package it.pagopa.pn.papertracker.middleware.dao.dynamo.entity;

import lombok.Data;
import lombok.Getter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@DynamoDbBean
@Data
public class ValidationFlow {

    public static final String COL_OCR_ENABLED = "ocrEnabled";
    public static final String COL_SEQUENCES_VALIDATION_TIMESTAMP = "sequencesValidationTimestamp";
    public static final String COL_OCR_REQUEST_TIMESTAMP = "ocrRequestTimestamp";
    public static final String COL_DEMAT_VALIDATION_TIMESTAMP = "dematValidationTimestamp";
    public static final String COL_VALIDATED_ATTACHMENT_TIMESTAMP = "validatedAttachmentTimestamp";
    public static final String COL_ATTACHMENT_URI = "attachmentUri";
    public static final String COL_VALIDATED_ATTACHMENT_TYPE = "validatedAttachmentType";

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_OCR_ENABLED)}))
    private Boolean ocrEnabled;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_SEQUENCES_VALIDATION_TIMESTAMP)}))
    private String sequencesValidationTimestamp;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_OCR_REQUEST_TIMESTAMP)}))
    private String ocrRequestTimestamp;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_DEMAT_VALIDATION_TIMESTAMP)}))
    private String dematValidationTimestamp;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_VALIDATED_ATTACHMENT_TIMESTAMP)}))
    private String validatedAttachmentTimestamp;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_ATTACHMENT_URI)}))
    private String attachmentUri;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_VALIDATED_ATTACHMENT_TYPE)}))
    private String validatedAttachmentType;
}
