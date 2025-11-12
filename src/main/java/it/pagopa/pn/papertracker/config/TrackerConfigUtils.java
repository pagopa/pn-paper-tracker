package it.pagopa.pn.papertracker.config;

import it.pagopa.pn.papertracker.exception.AttachmentsConfigNotFound;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Component
public class TrackerConfigUtils {

    private static final String SEPARATOR = ";";
    private static final int START_DATE_INDEX = 0;
    private static final String CONFIG_ERROR_CODE = "REQUIRED_CONFIG_NOT_FOUND";

    public record AttachmentsConfig(LocalDate startConfigurationTime, List<String> documentTypes) {}
    public record StrictValidationConfig(LocalDate startConfigurationTime, Boolean enableStrictValidation) { }

    private final List<AttachmentsConfig> requiredAttachmentsRefinementStock890Configs;
    private final List<AttachmentsConfig> sendOcrAttachmentsFinalValidationStock890Configs;
    private final List<AttachmentsConfig> sendOcrAttachmentsFinalValidationConfigs;
    private final List<StrictValidationConfig> strictFinalValidationStock890Config;

    public TrackerConfigUtils(PnPaperTrackerConfigs cfg) {
        this.requiredAttachmentsRefinementStock890Configs = buildAttachmentsConfigFromStringList(cfg.getRequiredAttachmentsRefinementStock890());
        this.sendOcrAttachmentsFinalValidationConfigs = buildAttachmentsConfigFromStringList(cfg.getSendOcrAttachmentsFinalValidation());
        this.sendOcrAttachmentsFinalValidationStock890Configs = buildAttachmentsConfigFromStringList(cfg.getSendOcrAttachmentsFinalValidationStock890());
        this.strictFinalValidationStock890Config = buildStrictFinalValidationStock890Config(cfg.getStrictFinalValidationStock890());
    }

    private  List<StrictValidationConfig> buildStrictFinalValidationStock890Config(List<String> strictFinalValidationStock890) {
        if(CollectionUtils.isEmpty(strictFinalValidationStock890)){
            return Collections.emptyList();
        }
        return strictFinalValidationStock890.stream()
                .map(this::toStrictValidationConfig)
                .sorted(Comparator.comparing(StrictValidationConfig::startConfigurationTime).reversed())
                .toList();
    }

    private StrictValidationConfig toStrictValidationConfig(String strictValidationConfig) {
        String[] strictValidationConfigSplit = strictValidationConfig.split(SEPARATOR);
        LocalDate startDate = LocalDate.parse(strictValidationConfigSplit[START_DATE_INDEX]);
        if(strictValidationConfigSplit.length > 1) {
            return new StrictValidationConfig(startDate, Boolean.parseBoolean(strictValidationConfigSplit[1]));
        }
        return new StrictValidationConfig(startDate, false);
    }


    private List<AttachmentsConfig> buildAttachmentsConfigFromStringList(List<String> attachmentsConfigsStr) {
        if(CollectionUtils.isEmpty(attachmentsConfigsStr)){
            return Collections.emptyList();
        }
        return attachmentsConfigsStr.stream()
                .map(this::toAttachmentsConfig)
                .sorted(Comparator.comparing(AttachmentsConfig::startConfigurationTime).reversed())
                .toList();
    }

    private AttachmentsConfig toAttachmentsConfig(String attachmentsConfigStr) {
        String[] printCapacitySplit = attachmentsConfigStr.split(SEPARATOR);
        LocalDate startDate = LocalDate.parse(printCapacitySplit[START_DATE_INDEX]);
        List<String> documentTypesArray = Arrays.asList(Arrays.copyOfRange(printCapacitySplit, 1, printCapacitySplit.length));
        return new AttachmentsConfig(startDate, documentTypesArray);
    }

    public List<String> getActualRequiredAttachmentsRefinementStock890(LocalDate startDate) {
        return requiredAttachmentsRefinementStock890Configs.stream()
                .filter(printCapacity -> startDate.isAfter(printCapacity.startConfigurationTime()))
                .findFirst()
                .map(AttachmentsConfig::documentTypes)
                .orElseThrow(() -> new AttachmentsConfigNotFound(CONFIG_ERROR_CODE,"RequiredAttachmentsRefinementStock890 not found for date: " + startDate));
    }

    public List<String> getActualSendOcrAttachmentsFinalValidationStock890(LocalDate startDate) {
        return sendOcrAttachmentsFinalValidationStock890Configs.stream()
                .filter(printCapacity -> startDate.isAfter(printCapacity.startConfigurationTime()))
                .findFirst()
                .map(AttachmentsConfig::documentTypes)
                .orElseThrow(() -> new AttachmentsConfigNotFound(CONFIG_ERROR_CODE,"SendOcrAttachmentsFinalValidationStock890 not found for date: " + startDate));
    }

    public List<String> getActualSendOcrAttachmentsFinalValidationConfigs(LocalDate startDate) {
        return sendOcrAttachmentsFinalValidationConfigs.stream()
                .filter(printCapacity -> startDate.isAfter(printCapacity.startConfigurationTime()))
                .findFirst()
                .map(AttachmentsConfig::documentTypes)
                .orElseThrow(() -> new AttachmentsConfigNotFound(CONFIG_ERROR_CODE,"SendOcrAttachmentsFinalValidation not found for date: " + startDate));
    }

    public Boolean getActualStrictFinalValidationStock890Config(LocalDate startDate) {
        return strictFinalValidationStock890Config.stream()
                .filter(printCapacity -> startDate.isAfter(printCapacity.startConfigurationTime()))
                .findFirst()
                .map(StrictValidationConfig::enableStrictValidation)
                .orElse(Boolean.FALSE);
    }
}
