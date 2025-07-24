package it.pagopa.pn.papertracker.middleware.dao.dynamo;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerConflictException;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static it.pagopa.pn.commons.abstractions.impl.AbstractDynamoKeyValueStore.ATTRIBUTE_NOT_EXISTS;
import static it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings.OCR_REQUEST_ID_INDEX;
import static software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional.keyEqualTo;

@Component
@Slf4j
public class PaperTrackingsDAOImpl extends BaseDao<PaperTrackings> implements PaperTrackingsDAO {

    private final String ERROR_CODE_PAPER_TRACKER_DUPLICATED_ITEM = "PN_PAPER_TRACKER_DUPLICATED_ITEM";

    public PaperTrackingsDAOImpl(DynamoDbEnhancedAsyncClient dynamoDbEnhancedClient, PnPaperTrackerConfigs cfg, DynamoDbAsyncClient dynamoDbAsyncClient) {
        super(dynamoDbEnhancedClient,
                dynamoDbAsyncClient,
                cfg.getDao().getPaperTrackingsTable(),
                PaperTrackings.class
        );
    }

    @Override
    public Mono<PaperTrackings> retrieveEntityByRequestId(String requestId) {
        return getByKey(Key.builder().partitionValue(requestId).build());
    }

    @Override
    public Flux<PaperTrackings> retrieveEntityByOcrRequestId(String ocrRequestId) {
        return retrieveFromIndex(OCR_REQUEST_ID_INDEX, keyEqualTo(Key.builder().partitionValue(ocrRequestId).build()));
    }

    @Override
    public Mono<PaperTrackings> putIfAbsent(PaperTrackings entity) {
        String expression = String.format("%s(%s)", ATTRIBUTE_NOT_EXISTS, PaperTrackings.COL_REQUEST_ID);

        return putIfAbsent(expression, entity)
                .onErrorMap(ConditionalCheckFailedException.class, ex -> {
                    log.error("Conditional check exception on PaperTrackingsDAOImpl putTrackings requestId={} exmessage={}", entity.getRequestId(), ex.getMessage());
                    return new PnPaperTrackerConflictException(ERROR_CODE_PAPER_TRACKER_DUPLICATED_ITEM, String.format("RequestId %s already exists", entity.getRequestId()));
                });
    }

    @Override
    public Mono<Void> updateItem(String requestId, Map<String, AttributeValue> attributeValueMap) {
        log.info("attributeValueMap {}", attributeValueMap);

        AtomicInteger counter = new AtomicInteger(0);

        Map<String, String> expressionAttributeNames = new HashMap<>();
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        List<String> updateExpressions = new ArrayList<>();

        expressionAttributeNames.put("#updatedAt", PaperTrackings.COL_UPDATED_AT);
        expressionAttributeValues.put(":updatedAt", AttributeValue.builder().s(Instant.now().toString()).build());
        updateExpressions.add("#updatedAt = :updatedAt");

        attributeValueMap.forEach((key, value) -> {
            List<String> expressions = buildUpdateExpressions(
                    key, value, counter, expressionAttributeNames, expressionAttributeValues
            );
            updateExpressions.addAll(expressions);
        });

        if (updateExpressions.isEmpty()) return Mono.empty();

        String updateExpr = "SET " + String.join(", ", updateExpressions);

        log.info("updateExpr {}", updateExpr);
        log.info("expressionAttributeValues {}", expressionAttributeValues);
        log.info("expressionAttributeNames {}", expressionAttributeNames);

        return update(
                Map.of("requestId", AttributeValue.builder().s(requestId).build()),
                updateExpr,
                expressionAttributeValues,
                expressionAttributeNames
        ).doOnError(e -> log.error("Error updating item with requestId {}: {}", requestId, e.getMessage()))
                .then();
    }

    /**
     * Builds update expressions for DynamoDB based on the attribute type.
     * <p>
     * For list attributes, generates an expression to append to the list using {@code list_append} and {@code if_not_exists}.
     * For map (inner object) attributes, generates expressions to update each inner key-value pair.
     * For other attribute types, generates a simple assignment expression.
     *
     * @param key     The attribute key to update.
     * @param value   The {@link AttributeValue} to set for the key.
     * @param counter An {@link AtomicInteger} used to generate unique parameter names.
     * @param names   A map of expression attribute names for DynamoDB.
     * @param values  A map of expression attribute values for DynamoDB.
     * @return A list of update expression strings for use in a DynamoDB update operation.
     */
    private List<String> buildUpdateExpressions(String key, AttributeValue value, AtomicInteger counter, Map<String, String> names, Map<String, AttributeValue> values) {
        List<String> expressions = new ArrayList<>();

        if (isListAttributeType(value)) {
            int idx = counter.getAndIncrement();
            names.put("#k" + idx, key);
            values.put(":v" + idx, AttributeValue.builder().l(value.l()).build());
            values.put(":empty" + idx, AttributeValue.builder().l(Collections.emptyList()).build());
            expressions.add("#k" + idx + " = list_append(if_not_exists(" + "#k" + idx + ", " + ":empty" + idx + "), " + ":v" + idx + ")");

        } else if (isInnerObjectAttributeType(value)) {
            value.m().forEach((innerKey, innerVal) -> {
                int idx = counter.getAndIncrement();
                names.put("#k" + idx, key);
                names.put("#k" + idx + "_inner", innerKey);
                values.put(":v" + idx, innerVal);
                expressions.add("#k" + idx + "." + "#k" + idx + "_inner" + " = " + ":v" + idx);
            });
        } else {
            int idx = counter.getAndIncrement();
            names.put("#k" + idx, key);
            values.put(":v" + idx, value);
            expressions.add("#k" + idx + " = " + ":v" + idx);
        }
        return expressions;
    }

    private static boolean isInnerObjectAttributeType(AttributeValue value) {
        return value.m() != null && !value.m().isEmpty();
    }

    private static boolean isListAttributeType(AttributeValue value) {
        return value.l() != null && !value.l().isEmpty();
    }
}
