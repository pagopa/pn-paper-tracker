package it.pagopa.pn.papertracker.mapper;

import it.pagopa.pn.papertracker.config.StatusCodeConfiguration;
import it.pagopa.pn.papertracker.generated.openapi.msclient.paperchannel.model.*;
import it.pagopa.pn.papertracker.model.HandlerContext;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class SendEventMapper {

    /**
     * Crea l'evento dalla classe PaperProgressStatusEvent e lo inserisce dentro PaperTrackins in modo da fare l'upsert
     *
     * @param handlerContext
     * @return PaperTrackings contenente il nuovo evento
     */
    public static Flux<SendEvent> createSendEventsFromPaperProgressStatusEvent(HandlerContext handlerContext) {
        return Mono.just(handlerContext.getPaperProgressStatusEvent())
                .flatMapMany(progressEvent ->
                     Flux.fromIterable(progressEvent.getAttachments().stream()
                            .map(attachmentDetails -> buildSendEvent(progressEvent, attachmentDetails))
                            .collect(Collectors.toList()))
                );
    }

    private static SendEvent buildSendEvent(PaperProgressStatusEvent progressEvent, AttachmentDetails attachmentDetails) {
        return SendEvent.builder()
                .attachments(List.of(attachmentDetails))
                .requestId(progressEvent.getRequestId())
                .statusCode(StatusCodeEnum.valueOf(StatusCodeConfiguration.StatusCodeConfigurationEnum.valueOf(progressEvent.getStatusCode()).getStatus().name()))
                .statusDetail(progressEvent.getStatusCode())
                .deliveryFailureCause(progressEvent.getDeliveryFailureCause())
                .registeredLetterCode(progressEvent.getRegisteredLetterCode())
                .discoveredAddress(buildAnalogAddressFromDiscoveredAddress(progressEvent.getDiscoveredAddress()))
                .statusDateTime(progressEvent.getStatusDateTime())
                .clientRequestTimeStamp(progressEvent.getClientRequestTimeStamp())
                .build();

    }

    private static AnalogAddress buildAnalogAddressFromDiscoveredAddress(DiscoveredAddress discoveredAddress) {
        return Objects.nonNull(discoveredAddress) ? AnalogAddress.builder()
                .address(discoveredAddress.getAddress())
                .addressRow2(discoveredAddress.getAddressRow2())
                .pr(discoveredAddress.getPr())
                .cap(discoveredAddress.getPr())
                .city(discoveredAddress.getCity())
                .city2(discoveredAddress.getCity2())
                .country(discoveredAddress.getCountry())
                .fullname(discoveredAddress.getName())
                .nameRow2(discoveredAddress.getNameRow2())
                .build() : null;
    }
}
