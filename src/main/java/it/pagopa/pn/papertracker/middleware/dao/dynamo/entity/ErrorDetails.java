package it.pagopa.pn.papertracker.middleware.dao.dynamo.entity;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@DynamoDbBean
@Data
public class ErrorDetails {

    public static final String COL_CAUSE = "cause";
    public static final String COL_MESSAGE = "message";

    private String cause;
    private String message;
}
