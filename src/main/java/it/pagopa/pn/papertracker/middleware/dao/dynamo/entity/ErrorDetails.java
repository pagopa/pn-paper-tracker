package it.pagopa.pn.papertracker.middleware.dao.dynamo.entity;

import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

@Setter
@Getter
@DynamoDbBean
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorDetails {

    public static final String COL_CAUSE = "cause";
    public static final String COL_MESSAGE = "message";
    public static final String COL_ADDITIONAL_DETAILS = "additionalDetails";

    private ErrorCause cause;
    private String message;
    private Map<String, AttributeValue> additionalDetails;
}
