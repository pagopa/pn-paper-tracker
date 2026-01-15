package it.pagopa.pn.papertracker.config;

import it.pagopa.pn.papertracker.exception.ConfigNotFound;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProcessingMode;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.model.OcrStatusEnum;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class TrackerConfigUtils {

    private static final String SEPARATOR = ";";
    private static final int START_DATE_INDEX = 0;
    private static final String CONFIG_ERROR_CODE = "REQUIRED_CONFIG_NOT_FOUND";

    public record AttachmentsConfig(LocalDate startConfigurationTime, List<String> documentTypes) {}
    public record StrictValidationConfig(LocalDate startConfigurationTime, Boolean enableStrictValidation) { }
    public record ActivationModeConfig(LocalDate startConfigurationTime, Map<ProductType, ProcessingMode> activationMode) { }

    private final List<AttachmentsConfig> requiredAttachmentsRefinementStock890Configs;
    private final List<AttachmentsConfig> sendOcrAttachmentsRefinementStock890Configs;
    private final List<AttachmentsConfig> sendOcrAttachmentsFinalValidationStock890Configs;
    private final List<AttachmentsConfig> sendOcrAttachmentsFinalValidationConfigs;
    private final List<StrictValidationConfig> strictFinalValidationStock890Config;
    private final List<ActivationModeConfig> productsProcessingModesConfig;
    private final PnPaperTrackerConfigs cfg;

    public TrackerConfigUtils(PnPaperTrackerConfigs cfg) {
        this.requiredAttachmentsRefinementStock890Configs = buildAttachmentsConfigFromStringList(cfg.getRequiredAttachmentsRefinementStock890());
        this.sendOcrAttachmentsRefinementStock890Configs = buildAttachmentsConfigFromStringList(cfg.getSendOcrAttachmentsRefinementStock890());
        this.sendOcrAttachmentsFinalValidationConfigs = buildAttachmentsConfigFromStringList(cfg.getSendOcrAttachmentsFinalValidation());
        this.sendOcrAttachmentsFinalValidationStock890Configs = buildAttachmentsConfigFromStringList(cfg.getSendOcrAttachmentsFinalValidationStock890());
        this.strictFinalValidationStock890Config = buildStrictFinalValidationStock890Config(cfg.getStrictFinalValidationStock890());
        this.productsProcessingModesConfig = buildActivationModeConfigFromStringList(cfg.getProductsProcessingModes());
        this.cfg = cfg;
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

    private List<ActivationModeConfig> buildActivationModeConfigFromStringList(List<String> productsProcessingModes) {
        if(CollectionUtils.isEmpty(productsProcessingModes)){
            return Collections.emptyList();
        }
        return productsProcessingModes.stream()
                .map(this::toActivationModeConfig)
                .sorted(Comparator.comparing(ActivationModeConfig::startConfigurationTime).reversed())
                .toList();
    }

    private AttachmentsConfig toAttachmentsConfig(String attachmentsConfigStr) {
        String[] attachmentsConfigSplit = attachmentsConfigStr.split(SEPARATOR);
        LocalDate startDate = LocalDate.parse(attachmentsConfigSplit[START_DATE_INDEX]);
        List<String> documentTypesArray = Arrays.asList(Arrays.copyOfRange(attachmentsConfigSplit, 1, attachmentsConfigSplit.length));
        return new AttachmentsConfig(startDate, documentTypesArray);
    }

    private ActivationModeConfig toActivationModeConfig(String activationModeConfigStr) {
        String[] activationModeConfigSplit = activationModeConfigStr.split(SEPARATOR);
        LocalDate startDate = LocalDate.parse(activationModeConfigSplit[START_DATE_INDEX]);
        Map<ProductType, ProcessingMode> activationModeMap = Arrays.stream(activationModeConfigSplit, 1, activationModeConfigSplit.length)
                .map(productMode -> productMode.split(":"))
                .collect(Collectors.toMap(productMode -> ProductType.fromValue(productMode[0]), productMode -> ProcessingMode.valueOf(productMode[1])));
        return new ActivationModeConfig(startDate, activationModeMap);
    }

    public List<String> getActualRequiredAttachmentsRefinementStock890(LocalDate startDate) {
        return requiredAttachmentsRefinementStock890Configs.stream()
                .filter(attachmentsConfig -> startDate.isAfter(attachmentsConfig.startConfigurationTime()))
                .findFirst()
                .map(AttachmentsConfig::documentTypes)
                .orElseThrow(() -> new ConfigNotFound(CONFIG_ERROR_CODE,"RequiredAttachmentsRefinementStock890 not found for date: " + startDate));
    }

    public List<String> getActualSendOcrAttachmentsRefinementStock890(LocalDate startDate) {
        return sendOcrAttachmentsRefinementStock890Configs.stream()
                .filter(attachmentsConfig -> startDate.isAfter(attachmentsConfig.startConfigurationTime()))
                .findFirst()
                .map(AttachmentsConfig::documentTypes)
                .orElseThrow(() -> new ConfigNotFound(CONFIG_ERROR_CODE,"SendOcrAttachmentsRefinementStock890 not found for date: " + startDate));
    }

    public List<String> getActualSendOcrAttachmentsFinalValidationStock890(LocalDate startDate) {
        return sendOcrAttachmentsFinalValidationStock890Configs.stream()
                .filter(attachmentsConfig -> startDate.isAfter(attachmentsConfig.startConfigurationTime()))
                .findFirst()
                .map(AttachmentsConfig::documentTypes)
                .orElseThrow(() -> new ConfigNotFound(CONFIG_ERROR_CODE,"SendOcrAttachmentsFinalValidationStock890 not found for date: " + startDate));
    }

    public List<String> getActualSendOcrAttachmentsFinalValidationConfigs(LocalDate startDate) {
        return sendOcrAttachmentsFinalValidationConfigs.stream()
                .filter(attachmentsConfig -> startDate.isAfter(attachmentsConfig.startConfigurationTime()))
                .findFirst()
                .map(AttachmentsConfig::documentTypes)
                .orElseThrow(() -> new ConfigNotFound(CONFIG_ERROR_CODE,"SendOcrAttachmentsFinalValidation not found for date: " + startDate));
    }

    public Boolean getActualStrictFinalValidationStock890Config(LocalDate startDate) {
        return strictFinalValidationStock890Config.stream()
                .filter(attachmentsConfig -> startDate.isAfter(attachmentsConfig.startConfigurationTime()))
                .findFirst()
                .map(StrictValidationConfig::enableStrictValidation)
                .orElse(Boolean.FALSE);
    }

    public Map<ProductType, OcrStatusEnum> getEnableOcrValidationFor() {
        return cfg.getEnableOcrValidationFor().stream()
                .map(config -> {
                    String[] splittedConfig = config.split(":");
                    return Map.entry(ProductType.fromValue(splittedConfig[0]), splittedConfig[1]);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> OcrStatusEnum.valueOf(entry.getValue())));
    }

    public Map<ProductType, ProcessingMode> getActualProductsProcessingModesConfig(LocalDate startDate) {
        return productsProcessingModesConfig.stream()
                .filter(activationModeConfig -> startDate.isAfter(activationModeConfig.startConfigurationTime()))
                .findFirst()
                .map(ActivationModeConfig::activationMode)
                .orElseThrow(() -> new ConfigNotFound(CONFIG_ERROR_CODE,"ProductsProcessingModes not found for date: " + startDate));
    }
}
