package it.pagopa.pn.papertracker.service.impl;

import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.PaperTrackerOutput;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.PaperTrackerOutputsResponse;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.PaperTrackerOutputsResponseResultInner;
import it.pagopa.pn.papertracker.generated.openapi.server.v1.dto.TrackingsRequest;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackerDryRunOutputsDAO;
import it.pagopa.pn.papertracker.service.PaperTrackerOutputService;
import it.pagopa.pn.papertracker.service.mapper.PaperTrackerOutputMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaperTrackerOutputServiceImpl implements PaperTrackerOutputService {

    private final PaperTrackerDryRunOutputsDAO paperTrackerDryRunOutputsDAO;

    @Override
    public Mono<PaperTrackerOutputsResponse> retrieveTrackingOutputs(TrackingsRequest trackingsRequest) {
        return Flux.fromIterable(trackingsRequest.getTrackingIds())
                .flatMap(trackingId -> paperTrackerDryRunOutputsDAO.retrieveOutputEvents(trackingId)
                        .collectList()
                        .map(paperTrackerDryRunOutputs -> {
                            List<PaperTrackerOutput> paperTrackerOutputList = paperTrackerDryRunOutputs.stream()
                                    .map(PaperTrackerOutputMapper::toDtoPaperTrackerOutput)
                                    .toList();
                            PaperTrackerOutputsResponseResultInner paperTrackerOutputsResponseResultInner = new PaperTrackerOutputsResponseResultInner();
                            paperTrackerOutputsResponseResultInner.setTrackingId(trackingId);
                            paperTrackerOutputsResponseResultInner.setOutputs(paperTrackerOutputList);
                            return paperTrackerOutputsResponseResultInner;
                        }))
                .collectList()
                .map(trackingErrorsResponseResultsInnerList -> {
                    PaperTrackerOutputsResponse paperTrackerOutputsResponse = new PaperTrackerOutputsResponse();
                    paperTrackerOutputsResponse.setResult(trackingErrorsResponseResultsInnerList);
                    return paperTrackerOutputsResponse;
                });
    }
}
