package it.pagopa.pn.papertracker.service.impl;

import it.pagopa.pn.papertracker.exception.PaperTrackerException;
import it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.SingleStatusUpdate;
import it.pagopa.pn.papertracker.mapper.MessageAttributeMapper;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.queue.model.CustomEventHeader;
import it.pagopa.pn.papertracker.middleware.queue.model.ExternalChannelEvent;
import it.pagopa.pn.papertracker.middleware.queue.producer.ExternalChannelToPaperChannelDryRunMomProducer;
import it.pagopa.pn.papertracker.middleware.queue.producer.ExternalChannelToPaperTrackerMomProducer;
import it.pagopa.pn.papertracker.service.SourceQueueProxyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

import java.time.Instant;
import java.util.UUID;

import static it.pagopa.pn.papertracker.generated.openapi.msclient.externalchannel.model.PaperProgressStatusEvent.JSON_PROPERTY_DISCOVERED_ADDRESS;
import static it.pagopa.pn.papertracker.utils.QueueConst.*;

/**
 * Service che gestisce il routing degli eventi provenienti da pn-ec in base alla modalità operativa.
 * <p>
 * Implementa la logica di proxy tra pn-ec e i componenti paper-tracker e paper-channel,
 * determinando il flusso di elaborazione in base alle all'attributo processingMode presente nella tabella pn-PaperTrackings.
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SourceQueueProxyServiceImpl implements SourceQueueProxyService {
    private static final String DRY_RUN_KEY = "dryRun";

    private final ExternalChannelToPaperTrackerMomProducer paperTrackerProducer;
    private final ExternalChannelToPaperChannelDryRunMomProducer paperChannelDryRunProducer;
    private final PaperTrackingsDAO paperTrackingsDAO;

    /**
     * Gestisce un messaggio proveniente da pn-ec applicando le regole di routing in base al processingMode.
     * <p>
     * Le regole applicate sono:
     * <ul>
     *   <li>Spedizione NON presente su pn-PaperTrackings -> evento inoltrato solo a pn-paper-channel</li>
     *   <li>Spedizione presente e modalità DRY -> evento inoltrato a pn-paper-channel e pn-paper-tracker</li>
     *   <li>Spedizione presente e modalità RUN -> evento inoltrato solo a pn-paper-tracker</li>
     * </ul>
     * </p>
     *
     * @param message il messaggio da pn-ec
     * @return Mono<Void>
     */
    public Mono<Void> handleExternalChannelMessage(
            Message<SingleStatusUpdate> message
    ) {
        log.info("Routing message");

        String requestId;
        try {
            requestId = message.getPayload().getAnalogMail().getRequestId();
        } catch (NullPointerException e) {
            return Mono.error(new PaperTrackerException("Malformed event from external-channel", e));
        }

        return paperTrackingsDAO.retrieveEntityByTrackingId(requestId)
                // caso: tracking NON trovato
                .switchIfEmpty(handlePaperChannelEvent(message).then(Mono.empty()))
                // caso: tracking trovato
                .flatMap(tracking -> handleByProcessingMode(tracking, message))
                .then();
    }

    /**
     * Gestisce un evento quando il tracking non è presente su pn-PaperTrackings.
     * L'evento viene inoltrato direttamente a pn-paper-channel in modalità dry-run.
     *
     * @param event l'evento da inoltrare
     * @return Mono<Void>
     */
    private Mono<Void> handlePaperChannelEvent(Message<SingleStatusUpdate> event) {
        return Mono.fromRunnable(() ->
                paperChannelDryRunProducer.push(buildOutputMessage(event, true))
        );
    }

    /**
     * Gestisce l'evento in base alla modalità operativa (DRY o RUN) recuperata dal tracking.
     * <p>
     * Modalità DRY: l'evento viene inoltrato sia a pn-paper-channel che a pn-paper-tracker.
     * Modalità RUN: l'evento viene inviato solo a pn-paper-tracker.
     * </p>
     *
     * @param tracking oggetto della spedizione
     * @param event l'evento da processare
     * @return Mono<Void>
     */
    private Mono<Void> handleByProcessingMode(
            PaperTrackings tracking,
            Message<SingleStatusUpdate> event
    ) {
        return switch (tracking.getProcessingMode()) {
            case DRY -> Mono.fromRunnable(() -> {
                var enrichedMessage = buildOutputMessage(event, true);
                paperChannelDryRunProducer.push(enrichedMessage);
                paperTrackerProducer.push(enrichedMessage);
            });
            case RUN -> Mono.fromRunnable(() ->
                    paperTrackerProducer.push(buildOutputMessage(event, false))
            );
        };
    }

    /**
     * Costruisce il messaggio di output da inoltrare ai consumer, arricchendolo con header e flag dry-run.
     *
     * @param message il messaggio originale da pn-ec
     * @param isDryRun header dryRun
     * @return evento arricchito pronto per essere inoltrato
     */
    private ExternalChannelEvent buildOutputMessage(Message<SingleStatusUpdate> message, boolean isDryRun) {
        var headers = MessageAttributeMapper.fromHeaders(message.getHeaders());
        headers.put(DRY_RUN_KEY, MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(String.valueOf(isDryRun))
                .build());
        return ExternalChannelEvent.builder()
                .header(CustomEventHeader.builder()
                        .publisher(PUBLISHER)
                        .eventId(UUID.randomUUID().toString())
                        .createdAt(Instant.now())
                        .eventType(TRACKER_QUEUE_PROXY_EVENT_TYPE)
                        .messageAttributes(headers)
                        .build())
                .payload(message.getPayload())
                .build();
    }
}