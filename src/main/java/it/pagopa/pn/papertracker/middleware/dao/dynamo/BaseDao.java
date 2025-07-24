package it.pagopa.pn.papertracker.middleware.dao.dynamo;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class BaseDao<T> {
    private final DynamoDbAsyncTable<T> tableAsync;
    private final Class<T> tClass;
    private final DynamoDbAsyncClient dynamoDbAsyncClient;

    protected BaseDao(
            DynamoDbEnhancedAsyncClient dynamoDbEnhancedAsyncClient,
            DynamoDbAsyncClient dynamoDbAsyncClient,
            String tableName,
            Class<T> tClass
    ) {
        this.tableAsync = dynamoDbEnhancedAsyncClient.table(tableName, TableSchema.fromBean(tClass));
        this.tClass = tClass;
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
    }

    protected Mono<T> putItem(T entity) {
        return Mono.fromFuture(this.tableAsync.putItem(entity))
                .thenReturn(entity);
    }

    public Flux<T> retrieveByRequestId(String pk) {
        log.info("retrieve Items from [{}] table, for requestId={}", tableAsync.tableName(), pk);
        return Flux.from(tableAsync.query(r -> r.queryConditional(
                        QueryConditional.keyEqualTo(Key.builder().partitionValue(pk).build()))))
                .flatMap(page -> Flux.fromIterable(page.items()))
                .doOnError(e -> log.error("Error retrieving items for requestId {}: {}", pk, e.getMessage()));
    }

    public Mono<T> getByKey(Key key) {
        return Mono.fromFuture(tableAsync.getItem(key));
    }

    public Flux<T> retrieveFromIndex(String indexName, QueryConditional queryConditional) {
        return Mono.from(tableAsync.index(indexName)
                        .query(QueryEnhancedRequest.builder().queryConditional(queryConditional).build()))
                .map(Page::items)
                .flatMapMany(Flux::fromIterable);
    }

    public Mono<T> putIfAbsent(String expression, T entity) {

        Expression conditionExpressionPut = Expression.builder()
                .expression(expression)
                .build();

        PutItemEnhancedRequest<T> request = PutItemEnhancedRequest.builder(tClass)
                .item(entity)
                .conditionExpression(conditionExpressionPut)
                .build();

        return Mono.fromFuture(tableAsync.putItem(request))
                .thenReturn(entity);
    }

    public Mono<UpdateItemResponse> updateIfExists(Map<String, AttributeValue> key, String updateExpression, Map<String, AttributeValue> expressionValues, Map<String, String> expressionNames, String conditionExpression) {
        UpdateItemRequest updateItemRequest = UpdateItemRequest.builder()
                .tableName(tableAsync.tableName())
                .key(key)
                .updateExpression(updateExpression)
                .expressionAttributeValues(expressionValues)
                .expressionAttributeNames(expressionNames)
                .conditionExpression(conditionExpression)
                .returnValues("ALL_NEW")
                .build();

        return Mono.fromFuture(dynamoDbAsyncClient.updateItem(updateItemRequest));
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
    protected List<String> buildUpdateExpressions(String key, AttributeValue value, AtomicInteger counter, Map<String, String> names, Map<String, AttributeValue> values) {
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
