package it.pagopa.pn.papertracker.middleware.dao.dynamo;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerConflictException;
import it.pagopa.pn.papertracker.exception.PnPaperTrackerNotFoundException;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static it.pagopa.pn.commons.abstractions.impl.AbstractDynamoKeyValueStore.ATTRIBUTE_NOT_EXISTS;
import static it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings.*;
import static software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional.keyEqualTo;

@Component
@Slf4j
public class PaperTrackingsDAOImpl extends BaseDao<PaperTrackings> implements PaperTrackingsDAO {

    private final String ERROR_CODE_PAPER_TRACKER_DUPLICATED_ITEM = "PN_PAPER_TRACKER_DUPLICATED_ITEM";
    private final String ERROR_CODE_PAPER_TRACKER_NOT_FOUND = "PN_PAPER_TRACKER_NOT_FOUND";

    public PaperTrackingsDAOImpl(DynamoDbEnhancedAsyncClient dynamoDbEnhancedClient, PnPaperTrackerConfigs cfg, DynamoDbAsyncClient dynamoDbAsyncClient) {
        super(dynamoDbEnhancedClient,
                dynamoDbAsyncClient,
                cfg.getDao().getPaperTrackingsTable(),
                PaperTrackings.class
        );
    }

    @Override
    public Mono<PaperTrackings> retrieveEntityByTrackingId(String trackingId) {
        return getByKey(Key.builder().partitionValue(trackingId).build());
    }

    @Override
    public Flux<PaperTrackings> retrieveEntityByAttemptId(String attemptId, String pcRetry) {
        Key.Builder key = Key.builder().partitionValue(attemptId);

        if(StringUtils.hasText(pcRetry)){
            key.sortValue(pcRetry);
        }

        return queryByIndex(key.build(), ATTEMPT_ID_PCRETRY_INDEX);
    }

    @Override
    public Flux<PaperTrackings> retrieveEntityByOcrRequestId(String ocrRequestId) {
        return retrieveFromIndex(OCR_REQUEST_ID_INDEX, keyEqualTo(Key.builder().partitionValue(ocrRequestId).build()));
    }

    @Override
    public Mono<PaperTrackings> putIfAbsent(PaperTrackings entity) {
        String expression = String.format("%s(%s)", ATTRIBUTE_NOT_EXISTS, PaperTrackings.COL_TRACKING_ID);

        return putIfAbsent(expression, entity)
                .onErrorMap(ConditionalCheckFailedException.class, ex -> {
                    log.error("Conditional check exception on PaperTrackingsDAOImpl putTrackings requestId={} exmessage={}", entity.getTrackingId(), ex.getMessage());
                    return new PnPaperTrackerConflictException(ERROR_CODE_PAPER_TRACKER_DUPLICATED_ITEM, String.format("RequestId %s already exists", entity.getTrackingId()));
                });
    }

    /**
     * Aggiorna un elemento PaperTrackings nel database DynamoDB, identificato dal requestId.
     * L'aggiornamento viene eseguito solo se l'elemento esiste (condizione attribute_exists).
     * Viene aggiornato anche il campo "updatedAt" con il timestamp corrente.
     *
     * @param requestId pk dell'oggetto PaperTrackings da aggiornare
     * @param paperTrackings l'oggetto PaperTrackings con i nuovi valori da aggiornare
     * @return un Mono contenente l'oggetto PaperTrackings aggiornato
     * @throws PnPaperTrackerNotFoundException se l'elemento con il requestId specificato non esiste
     */
    @Override
    public Mono<PaperTrackings> updateItem(String requestId, PaperTrackings paperTrackings) {
        log.info("Updating item with requestId: {}", requestId);

        Map<String, AttributeValue> attributeValueMap = PaperTrackings.paperTrackingsToAttributeValueMap(paperTrackings);
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

        String conditionExpression = String.format("%s(%s)", "attribute_exists", PaperTrackings.COL_TRACKING_ID);

        log.info("updateExpr {}", updateExpr);
        log.info("expressionAttributeValues {}", expressionAttributeValues);
        log.info("expressionAttributeNames {}", expressionAttributeNames);

        return updateIfExists(
                Map.of(PaperTrackings.COL_TRACKING_ID, AttributeValue.builder().s(requestId).build()),
                updateExpr,
                expressionAttributeValues,
                expressionAttributeNames,
                conditionExpression
        )
                .map(updateItemResponse -> PaperTrackings.attributeValueMapToPaperTrackings(updateItemResponse.attributes()))
                .doOnError(e -> log.error("Error updating item with requestId {}: {}", requestId, e.getMessage()))
                .onErrorMap(ConditionalCheckFailedException.class, ex -> {
                    log.error("Conditional check exception on PaperTrackingsDAOImpl updateTrackings requestId={} exmessage={}", requestId, ex.getMessage());
                    return new PnPaperTrackerNotFoundException(ERROR_CODE_PAPER_TRACKER_NOT_FOUND, String.format("RequestId %s does not exist", requestId));
                });
    }

    @Override
    public Flux<PaperTrackings> retrieveAllByTrackingIds(List<String> trackingIds) {
        return findAllByKeys(trackingIds);
    }

}
