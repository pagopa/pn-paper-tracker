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

import java.util.Map;

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

    public Mono<T> updateEntity(T entity) {
        UpdateItemEnhancedRequest<T> updateItemRequest = UpdateItemEnhancedRequest.builder(tClass)
                .item(entity)
                .ignoreNulls(true)
                .build();
        return Mono.fromFuture(tableAsync.updateItem(updateItemRequest));
    }

    public Mono<UpdateItemResponse> update(Map<String, AttributeValue> key, String updateExpression, Map<String, AttributeValue> expressionValues, Map<String, String> expressionNames) {
        UpdateItemRequest updateItemRequest = UpdateItemRequest.builder()
                .tableName(tableAsync.tableName())
                .key(key)
                .updateExpression(updateExpression)
                .expressionAttributeValues(expressionValues)
                .expressionAttributeNames(expressionNames)
                .build();

        return Mono.fromFuture(dynamoDbAsyncClient.updateItem(updateItemRequest));
    }

}
