package it.pagopa.pn.papertracker;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.config.TrackerConfigUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
public class TrackerConfigUtilsTest {

    @Test
    void returnsRequiredAttachmentsRefinementStock890Empty() {
        PnPaperTrackerConfigs cfg = new PnPaperTrackerConfigs();
        cfg.setRequiredAttachmentsRefinementStock890(List.of("2023-01-01", "2026-01-01;DOC1;DOC2", "2027-01-01;DOC3;DOC4"));
        TrackerConfigUtils utils = new TrackerConfigUtils(cfg);

        List<String> result = utils.getActualRequiredAttachmentsRefinementStock890(LocalDate.now());

        assertTrue(result.isEmpty());
    }

    @Test
    void returnsRequiredAttachmentsRefinementStock890OneItems() {
        PnPaperTrackerConfigs cfg = new PnPaperTrackerConfigs();
        cfg.setRequiredAttachmentsRefinementStock890(List.of("2023-01-01", "2024-01-01;DOC1", "2027-01-01;DOC3;DOC4"));
        TrackerConfigUtils utils = new TrackerConfigUtils(cfg);

        List<String> result = utils.getActualRequiredAttachmentsRefinementStock890(LocalDate.now());

        assertEquals(1, result.size());
    }

    @Test
    void returnsRequiredAttachmentsRefinementStock890MoreItems() {
        PnPaperTrackerConfigs cfg = new PnPaperTrackerConfigs();
        cfg.setRequiredAttachmentsRefinementStock890(List.of("2023-01-01", "2024-01-01;DOC1", "2025-01-01;DOC3;DOC4"));
        TrackerConfigUtils utils = new TrackerConfigUtils(cfg);

        List<String> result = utils.getActualRequiredAttachmentsRefinementStock890(LocalDate.now());

        assertEquals(2, result.size());
    }

    @Test
    void returnsSendOcrAttachmentsFinalValidationEmpty() {
        PnPaperTrackerConfigs cfg = new PnPaperTrackerConfigs();
        cfg.setSendOcrAttachmentsFinalValidation(List.of("2023-01-01", "2026-01-01;DOC1;DOC2", "2027-01-01;DOC3;DOC4"));
        TrackerConfigUtils utils = new TrackerConfigUtils(cfg);

        List<String> result = utils.getActualSendOcrAttachmentsFinalValidationConfigs(LocalDate.now());

        assertTrue(result.isEmpty());
    }

    @Test
    void returnsSendOcrAttachmentsFinalValidationOneItems() {
        PnPaperTrackerConfigs cfg = new PnPaperTrackerConfigs();
        cfg.setSendOcrAttachmentsFinalValidation(List.of("2023-01-01", "2024-01-01;DOC1", "2027-01-01;DOC3;DOC4"));
        TrackerConfigUtils utils = new TrackerConfigUtils(cfg);

        List<String> result = utils.getActualSendOcrAttachmentsFinalValidationConfigs(LocalDate.now());

        assertEquals(1, result.size());
    }

    @Test
    void returnsSendOcrAttachmentsFinalValidationMoreItems() {
        PnPaperTrackerConfigs cfg = new PnPaperTrackerConfigs();
        cfg.setSendOcrAttachmentsFinalValidation(List.of("2023-01-01", "2024-01-01;DOC1", "2025-01-01;DOC3;DOC4"));
        TrackerConfigUtils utils = new TrackerConfigUtils(cfg);

        List<String> result = utils.getActualSendOcrAttachmentsFinalValidationConfigs(LocalDate.now());

        assertEquals(2, result.size());
    }

    @Test
    void returnsSendOcrAttachmentsFinalValidationStock890Empty() {
        PnPaperTrackerConfigs cfg = new PnPaperTrackerConfigs();
        cfg.setSendOcrAttachmentsFinalValidationStock890(List.of("2023-01-01", "2026-01-01;DOC1;DOC2", "2027-01-01;DOC3;DOC4"));
        TrackerConfigUtils utils = new TrackerConfigUtils(cfg);

        List<String> result = utils.getActualSendOcrAttachmentsFinalValidationStock890(LocalDate.now());

        assertTrue(result.isEmpty());
    }

    @Test
    void returnsSendOcrAttachmentsFinalValidationStock890OneItems() {
        PnPaperTrackerConfigs cfg = new PnPaperTrackerConfigs();
        cfg.setSendOcrAttachmentsFinalValidationStock890(List.of("2023-01-01", "2024-01-01;DOC1", "2027-01-01;DOC3;DOC4"));
        TrackerConfigUtils utils = new TrackerConfigUtils(cfg);

        List<String> result = utils.getActualSendOcrAttachmentsFinalValidationStock890(LocalDate.now());

        assertEquals(1, result.size());
    }

    @Test
    void returnsSendOcrAttachmentsFinalValidationStock890MoreItems() {
        PnPaperTrackerConfigs cfg = new PnPaperTrackerConfigs();
        cfg.setSendOcrAttachmentsFinalValidationStock890(List.of("2023-01-01", "2024-01-01;DOC1", "2025-01-01;DOC3;DOC4"));
        TrackerConfigUtils utils = new TrackerConfigUtils(cfg);

        List<String> result = utils.getActualSendOcrAttachmentsFinalValidationStock890(LocalDate.now());

        assertEquals(2, result.size());
    }

    @Test
    void returnsStrictFinalValidationStock890Default() {
        PnPaperTrackerConfigs cfg = new PnPaperTrackerConfigs();
        cfg.setStrictFinalValidationStock890(List.of("2023-01-01", "2026-01-01;true", "2027-01-01;false"));
        TrackerConfigUtils utils = new TrackerConfigUtils(cfg);

        Boolean result = utils.getActualStrictFinalValidationStock890Config(LocalDate.now());

        assertFalse(result);
    }

    @Test
    void returnsStrictFinalValidationStock890true() {
        PnPaperTrackerConfigs cfg = new PnPaperTrackerConfigs();
        cfg.setStrictFinalValidationStock890(List.of("2023-01-01", "2024-01-01;true", "2027-01-01;false"));
        TrackerConfigUtils utils = new TrackerConfigUtils(cfg);

        Boolean result = utils.getActualStrictFinalValidationStock890Config(LocalDate.now());

        assertTrue(result);
    }

    @Test
    void returnsStrictFinalValidationStock890false() {
        PnPaperTrackerConfigs cfg = new PnPaperTrackerConfigs();
        cfg.setStrictFinalValidationStock890(List.of("2023-01-01", "2024-01-01;true", "2025-01-01;false"));
        TrackerConfigUtils utils = new TrackerConfigUtils(cfg);

        Boolean result = utils.getActualStrictFinalValidationStock890Config(LocalDate.now());

        assertFalse(result);
    }


}
