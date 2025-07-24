package it.pagopa.pn.papertracker.middleware.dao.dynamo.entity;

import lombok.Data;
import lombok.Getter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.time.Instant;

@DynamoDbBean
@Data
public class NotificationState {

    public static final String COL_REGISTERED_LETTER_CODE = "registeredLetterCode";
    public static final String COL_DELIVERY_FAILURE_CAUSE = "deliveryFailureCause";
    public static final String COL_DISCOVERED_ADDRESS = "discoveredAddress";
    public static final String COL_FINAL_STATUS_CODE = "finalStatusCode";
    public static final String COL_VALIDATED_SEQUENCE_TIMESTAMP = "validatedSequenceTimestamp";
    public static final String COL_VALIDATED_ATTACHMENT_URI = "validatedAttachmentUri";
    public static final String COL_VALIDATED_ATTACHMENT_TYPE = "validatedAttachmentType";

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_REGISTERED_LETTER_CODE)}))
    private String registeredLetterCode;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_DELIVERY_FAILURE_CAUSE)}))
    private String deliveryFailureCause;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_DISCOVERED_ADDRESS)}))
    private String discoveredAddress;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_FINAL_STATUS_CODE)}))
    private String finalStatusCode;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_VALIDATED_SEQUENCE_TIMESTAMP)}))
    private Instant validatedSequenceTimestamp;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_VALIDATED_ATTACHMENT_URI)}))
    private String validatedAttachmentUri;

    @Getter(onMethod = @__({@DynamoDbAttribute(COL_VALIDATED_ATTACHMENT_TYPE)}))
    private String validatedAttachmentType;

}
