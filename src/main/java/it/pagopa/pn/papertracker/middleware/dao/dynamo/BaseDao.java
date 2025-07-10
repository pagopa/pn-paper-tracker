package it.pagopa.pn.papertracker.middleware.dao.dynamo;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

@Slf4j
public class BaseDao<T> {
    private final DynamoDbAsyncTable<T> tableAsync;

    protected BaseDao(
            DynamoDbEnhancedAsyncClient dynamoDbEnhancedAsyncClient,
            String tableName,
            Class<T> tClass
    ) {
        this.tableAsync = dynamoDbEnhancedAsyncClient.table(tableName, TableSchema.fromBean(tClass));
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

}
