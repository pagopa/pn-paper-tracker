package it.pagopa.pn.papertracker.service;

import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.SequenceResponse;
import reactor.core.publisher.Mono;

public interface NotificationReworkService {

    Mono<SequenceResponse> notificationRework(String statusCode, String deliveryFailureCause);

    Mono<Void> updatePaperTrackingsStatusForRework(String trackingId, String reworkId);

}
