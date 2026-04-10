package it.pagopa.pn.papertracker.it.AR;

import it.pagopa.pn.papertracker.it.GenericTestCaseHandlerImpl;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackerDryRunOutputsDAO;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsErrorsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.middleware.queue.consumer.internal.ExternalChannelHandler;
import it.pagopa.pn.papertracker.middleware.queue.consumer.internal.OcrEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
public class TestCaseHandlerAR extends GenericTestCaseHandlerImpl {

    public TestCaseHandlerAR(ExternalChannelHandler externalChannelHandler, PaperTrackingsErrorsDAO paperTrackingsErrorsDAO, PaperTrackerDryRunOutputsDAO paperTrackerDryRunOutputsDao, PaperTrackingsDAO paperTrackingsDAO, OcrEventHandler ocrEventHandler) {
        super(externalChannelHandler, paperTrackingsErrorsDAO, paperTrackerDryRunOutputsDao, paperTrackingsDAO, ocrEventHandler);
    }

    @Override
    public String getProductType() {
        return ProductType.AR.getValue();
    }
}
