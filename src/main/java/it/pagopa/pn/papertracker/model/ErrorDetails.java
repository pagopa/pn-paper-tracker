package it.pagopa.pn.papertracker.model;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@DynamoDbBean
@Data
public class ErrorDetails {

    public static final String COL_CAUSE = "cause";
    public static final String COL_MESSAGE = "message";

    private String cause;
    private String message;
}
