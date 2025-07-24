package it.pagopa.pn.papertracker.middleware.dao;

import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

public interface PaperTrackingsDAO {

    Mono<PaperTrackings> putIfAbsent(PaperTrackings entity);

    Mono<PaperTrackings> retrieveEntityByRequestId(String requestId);

    Mono<Void> updateItem(String requestId, Map<String, AttributeValue> attributeValueMap);

    Flux<PaperTrackings> retrieveEntityByOcrRequestId(String ocrRequestId);
}
