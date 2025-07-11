package it.pagopa.pn.papertracker.middleware.dao.dynamo;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerConflictException;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.Event;
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

import java.util.Collections;
import java.util.Map;

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
    public Mono<Void> updateItem(PaperTrackings paperTrackings) {
        return updateEntity(paperTrackings)
                .doOnSuccess(r -> log.info("Validation flow updated successfully for requestId={}", paperTrackings.getRequestId()))
                .doOnError(e -> log.error("Error updating validation flow for requestId {}: {}", paperTrackings.getRequestId(), e.getMessage()))
                .then();
    }

    @Override
    public Mono<Void> addEvents(String requestId, Event event) {
        String updateExpression = "SET #events = list_append(if_not_exists(#events, :empty_list), :new_event)";
        Map<String, AttributeValue> key = Map.of(PaperTrackings.COL_REQUEST_ID, AttributeValue.builder().s(requestId).build());
        Map<String, String> expressionAttributeNames = Map.of("#events", PaperTrackings.COL_EVENTS);
        Map<String, AttributeValue> expressionAttributeValues = Map.of(
                ":new_event", AttributeValue.builder().l(Collections.singletonList(AttributeValue.builder().m(PaperTrackings.eventToAttributeValueMap(event)).build())).build(),
                ":empty_list", AttributeValue.builder().l(Collections.emptyList()).build()
        );

        return update(key, updateExpression, expressionAttributeValues, expressionAttributeNames)
                .doOnError(e -> log.error("Error updating item with requestId {}: {}", requestId, e.getMessage()))
                .then();
    }


}
