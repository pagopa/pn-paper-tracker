package it.pagopa.pn.papertracker.it.model;

import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackerDryRunOutputs;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackings;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.PaperTrackingsErrors;
import lombok.Data;

import java.util.List;

@Data
public class Expected {
    private List<PaperTrackingsErrors> errors;
    private List<PaperTrackerDryRunOutputs> outputs;
    private List<PaperTrackings> trackings;
}
