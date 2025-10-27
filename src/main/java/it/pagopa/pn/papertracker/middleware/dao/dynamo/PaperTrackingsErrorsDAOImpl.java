package it.pagopa.pn.papertracker.middleware.dao.dynamo;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsErrorsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsErrors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

import java.time.Instant;

@Component
@Slf4j
public class PaperTrackingsErrorsDAOImpl extends BaseDao<PaperTrackingsErrors> implements PaperTrackingsErrorsDAO {

    private final PnPaperTrackerConfigs pnPaperTrackerConfigs;

    public PaperTrackingsErrorsDAOImpl(DynamoDbEnhancedAsyncClient dynamoDbEnhancedClient, PnPaperTrackerConfigs cfg, DynamoDbAsyncClient dynamoDbAsyncClient) {
        super(dynamoDbEnhancedClient,
                dynamoDbAsyncClient,
                cfg.getDao().getPaperTrackingsErrorsTable(),
                PaperTrackingsErrors.class
        );
        this.pnPaperTrackerConfigs = cfg;
    }

    public Mono<PaperTrackingsErrors> insertError(PaperTrackingsErrors paperTrackingsErrors) {
        paperTrackingsErrors.setTtl(Instant.now().plus(pnPaperTrackerConfigs.getPaperTrackingsErrorsTtlDuration()).getEpochSecond());
        return putItem(paperTrackingsErrors);
    }

    public Flux<PaperTrackingsErrors> retrieveErrors(String requestId) {
        return retrieveByRequestId(requestId);
    }
}
