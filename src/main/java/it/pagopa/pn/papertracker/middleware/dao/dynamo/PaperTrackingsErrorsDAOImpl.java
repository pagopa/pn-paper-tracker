package it.pagopa.pn.papertracker.middleware.dao.dynamo;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.middleware.dao.*;
import it.pagopa.pn.papertracker.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

@Component
@Slf4j
public class PaperTrackingsErrorsDAOImpl implements PaperTrackingsErrorsDAO {

    private final DynamoDbAsyncTable<PaperTrackingsErrors> table;

    public PaperTrackingsErrorsDAOImpl(DynamoDbEnhancedAsyncClient dynamoDbEnhancedClient, PnPaperTrackerConfigs cfg) {
        this.table = dynamoDbEnhancedClient.table(cfg.getDao().getPaperTrackingsErrorsTableName(), TableSchema.fromBean(PaperTrackingsErrors.class));
    }

    public Mono<PaperTrackingsErrors> insertError(PaperTrackingsErrors entity) {
        PutItemEnhancedRequest<PaperTrackingsErrors> request = PutItemEnhancedRequest.builder(PaperTrackingsErrors.class)
                .item(entity)
                .build();

        return Mono.fromFuture(table.putItem(request))
                .doOnError(e -> log.error("Error putting item with requestId {}: {}", entity.getRequestId(), e.getMessage()))
                .thenReturn(entity);
    }

    public Flux<PaperTrackingsErrors> retrieveErrors(String requestId) {
        log.info("retrieveErrors for requestId={}", requestId);
        return Flux.from(table.query(r -> r.queryConditional(
                        QueryConditional.keyEqualTo(Key.builder().partitionValue(requestId).build()))))
                .flatMap(page -> Flux.fromIterable(page.items()))
                .doOnError(e -> log.error("Error retrieving errors for requestId {}: {}", requestId, e.getMessage()));
    }
}
