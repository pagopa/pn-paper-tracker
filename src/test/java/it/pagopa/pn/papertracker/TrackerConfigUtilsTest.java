package it.pagopa.pn.papertracker;

import it.pagopa.pn.papertracker.config.PnPaperTrackerConfigs;
import it.pagopa.pn.papertracker.config.TrackerConfigUtils;
import it.pagopa.pn.papertracker.exception.ConfigNotFound;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProcessingMode;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
public class TrackerConfigUtilsTest {

    private final LocalDate startDate = LocalDate.of(2025, 3, 2);

    @Test
    void returnsRequiredAttachmentsRefinementStock890Empty() {
        PnPaperTrackerConfigs cfg = new PnPaperTrackerConfigs();
        cfg.setRequiredAttachmentsRefinementStock890(List.of("2023-01-01", "2026-01-01;DOC1;DOC2", "2027-01-01;DOC3;DOC4"));
        TrackerConfigUtils utils = new TrackerConfigUtils(cfg);

        List<String> result = utils.getActualRequiredAttachmentsRefinementStock890(startDate);

        assertTrue(result.isEmpty());
    }

    @Test
    void returnsRequiredAttachmentsRefinementStock890OneItems() {
        PnPaperTrackerConfigs cfg = new PnPaperTrackerConfigs();
        cfg.setRequiredAttachmentsRefinementStock890(List.of("2023-01-01", "2024-01-01;DOC1", "2027-01-01;DOC3;DOC4"));
        TrackerConfigUtils utils = new TrackerConfigUtils(cfg);

        List<String> result = utils.getActualRequiredAttachmentsRefinementStock890(startDate);

        assertEquals(1, result.size());
    }

    @Test
    void returnsRequiredAttachmentsRefinementStock890MoreItems() {
        PnPaperTrackerConfigs cfg = new PnPaperTrackerConfigs();
        cfg.setRequiredAttachmentsRefinementStock890(List.of("2023-01-01", "2024-01-01;DOC1", "2025-01-01;DOC3;DOC4"));
        TrackerConfigUtils utils = new TrackerConfigUtils(cfg);

        List<String> result = utils.getActualRequiredAttachmentsRefinementStock890(startDate);

        assertEquals(2, result.size());
    }

    @Test
    void returnsSendOcrAttachmentsFinalValidationEmpty() {
        PnPaperTrackerConfigs cfg = new PnPaperTrackerConfigs();
        cfg.setSendOcrAttachmentsFinalValidation(List.of("2023-01-01", "2026-01-01;DOC1;DOC2", "2027-01-01;DOC3;DOC4"));
        TrackerConfigUtils utils = new TrackerConfigUtils(cfg);

        List<String> result = utils.getActualSendOcrAttachmentsFinalValidationConfigs(startDate);

        assertTrue(result.isEmpty());
    }

    @Test
    void returnsSendOcrAttachmentsFinalValidationOneItems() {
        PnPaperTrackerConfigs cfg = new PnPaperTrackerConfigs();
        cfg.setSendOcrAttachmentsFinalValidation(List.of("2023-01-01", "2024-01-01;DOC1", "2027-01-01;DOC3;DOC4"));
        TrackerConfigUtils utils = new TrackerConfigUtils(cfg);

        List<String> result = utils.getActualSendOcrAttachmentsFinalValidationConfigs(startDate);

        assertEquals(1, result.size());
    }

    @Test
    void returnsSendOcrAttachmentsFinalValidationMoreItems() {
        PnPaperTrackerConfigs cfg = new PnPaperTrackerConfigs();
        cfg.setSendOcrAttachmentsFinalValidation(List.of("2023-01-01", "2024-01-01;DOC1", "2025-01-01;DOC3;DOC4"));
        TrackerConfigUtils utils = new TrackerConfigUtils(cfg);

        List<String> result = utils.getActualSendOcrAttachmentsFinalValidationConfigs(startDate);

        assertEquals(2, result.size());
    }

    @Test
    void returnsSendOcrAttachmentsFinalValidationStock890Empty() {
        PnPaperTrackerConfigs cfg = new PnPaperTrackerConfigs();
        cfg.setSendOcrAttachmentsFinalValidationStock890(List.of("2023-01-01", "2026-01-01;DOC1;DOC2", "2027-01-01;DOC3;DOC4"));
        TrackerConfigUtils utils = new TrackerConfigUtils(cfg);

        List<String> result = utils.getActualSendOcrAttachmentsFinalValidationStock890(startDate);

        assertTrue(result.isEmpty());
    }

    @Test
    void returnsSendOcrAttachmentsFinalValidationStock890OneItems() {
        PnPaperTrackerConfigs cfg = new PnPaperTrackerConfigs();
        cfg.setSendOcrAttachmentsFinalValidationStock890(List.of("2023-01-01", "2024-01-01;DOC1", "2027-01-01;DOC3;DOC4"));
        TrackerConfigUtils utils = new TrackerConfigUtils(cfg);

        List<String> result = utils.getActualSendOcrAttachmentsFinalValidationStock890(startDate);

        assertEquals(1, result.size());
    }

    @Test
    void returnsSendOcrAttachmentsFinalValidationStock890MoreItems() {
        PnPaperTrackerConfigs cfg = new PnPaperTrackerConfigs();
        cfg.setSendOcrAttachmentsFinalValidationStock890(List.of("2023-01-01", "2024-01-01;DOC1", "2025-01-01;DOC3;DOC4"));
        TrackerConfigUtils utils = new TrackerConfigUtils(cfg);

        List<String> result = utils.getActualSendOcrAttachmentsFinalValidationStock890(startDate);

        assertEquals(2, result.size());
    }

    @Test
    void returnsStrictFinalValidationStock890Default() {
        PnPaperTrackerConfigs cfg = new PnPaperTrackerConfigs();
        cfg.setStrictFinalValidationStock890(List.of("2023-01-01", "2026-01-01;true", "2027-01-01;false"));
        TrackerConfigUtils utils = new TrackerConfigUtils(cfg);

        Boolean result = utils.getActualStrictFinalValidationStock890Config(startDate);

        assertFalse(result);
    }

    @Test
    void returnsStrictFinalValidationStock890true() {
        PnPaperTrackerConfigs cfg = new PnPaperTrackerConfigs();
        cfg.setStrictFinalValidationStock890(List.of("2023-01-01", "2024-01-01;true", "2027-01-01;false"));
        TrackerConfigUtils utils = new TrackerConfigUtils(cfg);

        Boolean result = utils.getActualStrictFinalValidationStock890Config(startDate);

        assertTrue(result);
    }

    @Test
    void returnsStrictFinalValidationStock890false() {
        PnPaperTrackerConfigs cfg = new PnPaperTrackerConfigs();
        cfg.setStrictFinalValidationStock890(List.of("2023-01-01", "2024-01-01;true", "2025-01-01;false"));
        TrackerConfigUtils utils = new TrackerConfigUtils(cfg);

        Boolean result = utils.getActualStrictFinalValidationStock890Config(startDate);

        assertFalse(result);
    }

    @Test
    void returnsProductsProcessingModes() {
        PnPaperTrackerConfigs cfg = new PnPaperTrackerConfigs();
        cfg.setProductsProcessingModes(List.of("1970-01-01;AR:RUN;RIR:RUN", "2026-02-02;AR:RUN;RIR:RUN;890:DRY"));
        Map<ProductType, ProcessingMode> resultExpected = Map.of(
                ProductType.AR, ProcessingMode.RUN,
                ProductType.RIR, ProcessingMode.RUN
        );
        TrackerConfigUtils utils = new TrackerConfigUtils(cfg);

        Map<ProductType, ProcessingMode> result = utils.getActualProductsProcessingModesConfig(startDate);

        assertEquals(resultExpected, result);
    }

    @Test
    void returnsProductsProcessingModesStartDateAfterTwo() {
        PnPaperTrackerConfigs cfg = new PnPaperTrackerConfigs();
        cfg.setProductsProcessingModes(List.of("2024-04-12;890:RUN", "2025-03-01;AR:RUN;RIR:RUN;890:DRY"));
        Map<ProductType, ProcessingMode> resultExpected = Map.of(
                ProductType.AR, ProcessingMode.RUN,
                ProductType.RIR, ProcessingMode.RUN,
                ProductType._890, ProcessingMode.DRY
        );
        TrackerConfigUtils utils = new TrackerConfigUtils(cfg);

        Map<ProductType, ProcessingMode> result = utils.getActualProductsProcessingModesConfig(startDate);

        assertEquals(resultExpected, result);
    }

    @Test
    void returnsProductsProcessingModesNotFound() {
        PnPaperTrackerConfigs cfg = new PnPaperTrackerConfigs();
        cfg.setProductsProcessingModes(List.of("2026-02-02;AR:RUN;RIR:RUN;890:DRY"));
        TrackerConfigUtils utils = new TrackerConfigUtils(cfg);

        assertThrows(
                ConfigNotFound.class,
                () -> utils.getActualProductsProcessingModesConfig(startDate)
        );
    }

}
