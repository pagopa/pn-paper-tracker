package it.pagopa.pn.papertracker.it.RIS;

import it.pagopa.pn.papertracker.it.GenericTestCaseHandlerImpl;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackerDryRunOutputsDAO;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsDAO;
import it.pagopa.pn.papertracker.middleware.dao.PaperTrackingsErrorsDAO;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.middleware.queue.consumer.internal.ExternalChannelHandler;
import it.pagopa.pn.papertracker.middleware.queue.consumer.internal.OcrEventHandler;
import org.springframework.stereotype.Component;

@Component
public class TestCaseHandlerRIS extends GenericTestCaseHandlerImpl {

    public TestCaseHandlerRIS(ExternalChannelHandler externalChannelHandler, PaperTrackingsErrorsDAO paperTrackingsErrorsDAO, PaperTrackerDryRunOutputsDAO paperTrackerDryRunOutputsDao, PaperTrackingsDAO paperTrackingsDAO, OcrEventHandler ocrEventHandler) {
        super(externalChannelHandler, paperTrackingsErrorsDAO, paperTrackerDryRunOutputsDao, paperTrackingsDAO, ocrEventHandler);
    }

    @Override
    public String getProductType() {
        return  ProductType.RIS.getValue();
    }

}
