package it.pagopa.pn.papertracker.middleware.dao.dynamo;

import it.pagopa.pn.commons.exceptions.PnIdConflictException;
import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Attachment;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Event;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsEntity;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ValidationFlow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static it.pagopa.pn.commons.abstractions.impl.AbstractDynamoKeyValueStore.ATTRIBUTE_NOT_EXISTS;
import static software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional.keyEqualTo;

@Component
@Slf4j
public class PaperTrackingsDAOImpl implements PaperTrackingsDAO {

    private final DynamoDbAsyncTable<PaperTrackingsEntity> table;
    private final DynamoDbAsyncClient dynamoDbAsyncClient;

    private final String ERROR_CODE_PAPER_TRACKER_DUPLICATED_ITEM = "PN_PAPER_TRACKER_DUPLICATED_ITEM";

    public PaperTrackingsDAOImpl(DynamoDbEnhancedAsyncClient dynamoDbEnhancedClient, PnPaperTrackerConfigs cfg, DynamoDbAsyncClient dynamoDbAsyncClient) {
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
        this.table = dynamoDbEnhancedClient.table(cfg.getDao().getPaperTrackingsTableName(), TableSchema.fromBean(PaperTrackingsEntity.class));
    }

    @Override
    public Mono<PaperTrackingsEntity> putPaperTrackings(PaperTrackingsEntity entity) {
        log.info("putPaperTrackings for requestId={}", entity.getRequestId());

        String expression = String.format(
                "%s(%s)",
                ATTRIBUTE_NOT_EXISTS,
                PaperTrackingsEntity.COL_REQUEST_ID
        );

        Expression conditionExpressionPut = Expression.builder()
                .expression(expression)
                .build();

        PutItemEnhancedRequest<PaperTrackingsEntity> request = PutItemEnhancedRequest.builder(PaperTrackingsEntity.class)
                .item(entity)
                .conditionExpression(conditionExpressionPut)
                .build();

        return Mono.fromFuture(table.putItem(request))
                .onErrorMap(ConditionalCheckFailedException.class, ex -> {
                    log.warn("Conditional check exception on PaperTrackingsDAOImpl putTrackings requestId={} exmessage={}", entity.getRequestId(), ex.getMessage());
                    return new PnIdConflictException(
                            ERROR_CODE_PAPER_TRACKER_DUPLICATED_ITEM,
                            Collections.singletonMap("requestId", entity.getRequestId()),
                            ex
                    );
                })
                .thenReturn(entity);
    }

    @Override
    public Mono<PaperTrackingsEntity> getPaperTrackings(String requestId) {
        log.info("getPaperTrackings for requestId={}", requestId);

        GetItemEnhancedRequest request = GetItemEnhancedRequest.builder()
                .key(key -> key.partitionValue(requestId))
                .build();

        return Mono.fromFuture(table.getItem(request));
    }

    @Override
    public Mono<Void> updatePaperTrackingsEvent(String requestId, Event event) {
        log.info("updatePaperTrackingsEvent for requestId={}", requestId);

        String updateExpression = "SET #events = list_append(if_not_exists(#events, :empty_list), :event)";

        Map<String, String> expressionAttributeNames = Map.of(
                "#events", PaperTrackingsEntity.COL_EVENTS
        );

        Map<String, AttributeValue> expressionAttributeValues = Map.of(
                ":event", AttributeValue.builder()
                        .l(Collections.singletonList(AttributeValue.builder()
                                .m(eventToAttributeValueMap(event))
                                .build()))
                        .build(),
                ":empty_list", AttributeValue.builder()
                        .l(Collections.emptyList())
                        .build()
        );

        return updateItem(requestId, updateExpression, expressionAttributeNames, expressionAttributeValues);
    }

    private Map<String, AttributeValue> eventToAttributeValueMap(Event event) {
        Map<String, AttributeValue> map = new HashMap<>();
        map.put(Event.COL_REQUEST_TIMESTAMP, AttributeValue.builder().s(event.getRequestTimestamp()).build());
        map.put(Event.COL_STATUS_CODE, AttributeValue.builder().s(event.getStatusCode()).build());
        map.put(Event.COL_STATUS_TIMESTAMP, AttributeValue.builder().s(event.getStatusTimestamp()).build());
        map.put(Event.COL_PRODUCT_TYPE, AttributeValue.builder().s(String.valueOf(event.getProductType())).build());
        map.put(Event.COL_DELIVERY_FAILURE_CAUSE, AttributeValue.builder().s(event.getDeliveryFailureCause()).build());
        map.put(Event.COL_DISCOVERED_ADDRESS, AttributeValue.builder().s(event.getDiscoveredAddress()).build());
        if (event.getAttachments() != null) {
            map.put(Event.COL_ATTACHMENTS, AttributeValue.builder()
                    .l(event.getAttachments().stream()
                            .map(attachment -> AttributeValue.builder().m(attachmentToAttributeValueMap(attachment)).build())
                            .toList())
                    .build());
        } else {
            map.put(Event.COL_ATTACHMENTS, AttributeValue.builder().l(Collections.emptyList()).build());
        }

        return map;
    }

    private Map<String, AttributeValue> attachmentToAttributeValueMap(Attachment attachment) {
        Map<String, AttributeValue> map = new HashMap<>();
        map.put(Attachment.COL_ID, AttributeValue.builder().s(attachment.getId()).build());
        map.put(Attachment.COL_DOCUMENT_TYPE, AttributeValue.builder().s(attachment.getDocumentType()).build());
        map.put(Attachment.COL_URL, AttributeValue.builder().s(attachment.getUrl()).build());
        map.put(Attachment.COL_DATE, AttributeValue.builder()
                .s(attachment.getDate() != null ? attachment.getDate().toString() : null)
                .build()); //TODO da verificare
        return map;
    }

    @Override
    public Mono<Void> updatePaperTrackingsValidationFlow(String requestId, ValidationFlow validationFlow) {
        Map<String, AttributeValue> validationFlowMap = validationFlowToAttributeValueMap(validationFlow);
        String updateExpression = "SET #vf = :vf";
        Map<String, String> expressionAttributeNames = Map.of("#vf", PaperTrackingsEntity.COL_VALIDATION_FLOW);
        Map<String, AttributeValue> expressionAttributeValues = Map.of(":vf", AttributeValue.builder().m(validationFlowMap).build());

        return updateItem(requestId, updateExpression, expressionAttributeNames, expressionAttributeValues);
    }

    private Map<String, AttributeValue> validationFlowToAttributeValueMap(ValidationFlow validationFlow) {
        Map<String, AttributeValue> validationFlowMap = new HashMap<>();
        if (validationFlow.getOcrEnabled() != null)
            validationFlowMap.put(ValidationFlow.COL_OCR_ENABLED, AttributeValue.builder().bool(validationFlow.getOcrEnabled()).build());
        if (validationFlow.getSequencesValidationTimestamp() != null)
            validationFlowMap.put(ValidationFlow.COL_SEQUENCES_VALIDATION_TIMESTAMP, AttributeValue.builder().s(validationFlow.getSequencesValidationTimestamp()).build());
        if (validationFlow.getOcrRequestTimestamp() != null)
            validationFlowMap.put(ValidationFlow.COL_OCR_REQUEST_TIMESTAMP, AttributeValue.builder().s(validationFlow.getOcrRequestTimestamp()).build());
        if (validationFlow.getDematValidationTimestamp() != null)
            validationFlowMap.put(ValidationFlow.COL_DEMAT_VALIDATION_TIMESTAMP, AttributeValue.builder().s(validationFlow.getDematValidationTimestamp()).build());
        if (validationFlow.getValidatedAttachmentTimestamp() != null)
            validationFlowMap.put(ValidationFlow.COL_VALIDATED_ATTACHMENT_TIMESTAMP, AttributeValue.builder().s(validationFlow.getValidatedAttachmentTimestamp()).build());
        if (validationFlow.getAttachmentUri() != null)
            validationFlowMap.put(ValidationFlow.COL_ATTACHMENT_URI, AttributeValue.builder().s(validationFlow.getAttachmentUri()).build());
        if (validationFlow.getValidatedAttachmentType() != null)
            validationFlowMap.put(ValidationFlow.COL_VALIDATED_ATTACHMENT_TYPE, AttributeValue.builder().s(validationFlow.getValidatedAttachmentType()).build());
        return validationFlowMap;
    }

    private Mono<Void> updateItem(String requestId, String updateExpression, Map<String, String> expressionAttributeNames, Map<String, AttributeValue> expressionAttributeValues) {
        UpdateItemRequest updateItemRequest = UpdateItemRequest.builder()
                .tableName(table.tableName())
                .key(Map.of(PaperTrackingsEntity.COL_REQUEST_ID, AttributeValue.builder().s(requestId).build()))
                .updateExpression(updateExpression)
                .expressionAttributeNames(expressionAttributeNames)
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        return Mono.fromFuture(dynamoDbAsyncClient.updateItem(updateItemRequest))
                .doOnSuccess(r -> log.info("Update successful for requestId={}", requestId))
                .doOnError(e -> log.error("Error updating item with requestId {}: {}", requestId, e.getMessage()))
                .then();
    }

    @Override
    public Flux<PaperTrackingsEntity> queryPaperTrackings(String ocrRequestId) {
        log.info("queryPaperTrackings for ocrRequestId={}", ocrRequestId);

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(keyEqualTo(Key.builder().partitionValue(ocrRequestId).build()))
                .build();

        return Flux.from(table.query(request))
                .flatMap(page -> Flux.fromIterable(page.items()));
    }
}
