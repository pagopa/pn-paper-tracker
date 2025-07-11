package it.pagopa.pn.papertracker.middleware.dao.dynamo;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackerDryRunOutputsDAO;
import it.pagopa.pn.papertracker.model.PaperTrackerDryRunOutputs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;

@Slf4j
@Component
public class PaperTrackerDryRunOutputsDAOImpl extends BaseDao<PaperTrackerDryRunOutputs> implements PaperTrackerDryRunOutputsDAO {

    public PaperTrackerDryRunOutputsDAOImpl(DynamoDbEnhancedAsyncClient dynamoDbEnhancedClient, PnPaperTrackerConfigs cfg) {
        super(dynamoDbEnhancedClient,
                cfg.getDao().getPaperTrackerDryRunOutputsTable(),
                PaperTrackerDryRunOutputs.class
        );
    }

    @Override
    public Flux<PaperTrackerDryRunOutputs> retrieveOutputEvents(String requestId) {
        log.info("retrieving output events for requestId={}", requestId);
        return retrieveByRequestId(requestId);
    }

    @Override
    public Mono<PaperTrackerDryRunOutputs> insertOutputEvent(PaperTrackerDryRunOutputs paperTrackerDryRunOutputs) {
        return putItem(paperTrackerDryRunOutputs);
    }
}
