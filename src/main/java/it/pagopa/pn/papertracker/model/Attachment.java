package it.pagopa.pn.papertracker.model;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.time.Instant;

@DynamoDbBean
@Data
public class Attachment {
    private Instant date;
    private String id;
    private String documentType;
    private String url;
}
