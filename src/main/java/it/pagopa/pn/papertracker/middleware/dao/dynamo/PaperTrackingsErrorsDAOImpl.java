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
public class PaperTrackingsErrorsDAOImpl extends BaseDao<PaperTrackingsErrors> implements PaperTrackingsErrorsDAO {

    public PaperTrackingsErrorsDAOImpl(DynamoDbEnhancedAsyncClient dynamoDbEnhancedClient, PnPaperTrackerConfigs cfg) {
        super(dynamoDbEnhancedClient,
                cfg.getDao().getPaperTrackingsErrorsTable(),
                PaperTrackingsErrors.class
        );
    }

    public Mono<PaperTrackingsErrors> insertError(PaperTrackingsErrors paperTrackingsErrors) {
        return putItem(paperTrackingsErrors);
    }

    public Flux<PaperTrackingsErrors> retrieveErrors(String requestId) {
        return retrieveByRequestId(requestId);
    }
}
