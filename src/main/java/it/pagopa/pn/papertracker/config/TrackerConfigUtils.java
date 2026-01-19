package it.pagopa.pn.papertracker.config;

import it.pagopa.pn.papertracker.exception.ConfigNotFound;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProcessingMode;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.model.OcrStatusEnum;
import lombok.*;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class TrackerConfigUtils {

    private static final String SEPARATOR = ";";
    private static final String CONFIG_ERROR_CODE = "REQUIRED_CONFIG_NOT_FOUND";


    @Getter
    public abstract static class ConfigWithDate<T> {

        protected final LocalDate startConfigurationTime;
        protected final List<String> stringConfigs;

        protected ConfigWithDate(String configWithDate) {
            var splitted = configWithDate.split(SEPARATOR);

            this.startConfigurationTime = LocalDate.parse(splitted[0]);
            this.stringConfigs = List.copyOf(
                    Arrays.asList(Arrays.copyOfRange(splitted, 1, splitted.length))
            );
        }

        public abstract T getConfig();
    }

    public static final class ListStringConfig extends ConfigWithDate<List<String>> {

        public ListStringConfig(String configWithDate) {
            super(configWithDate);
        }

        @Override
        public List<String> getConfig() {
            return stringConfigs;
        }
    }

    public static final class BooleanConfig extends ConfigWithDate<Boolean> {

        public BooleanConfig(String configWithDate) {
            super(configWithDate);
        }

        @Override
        public Boolean getConfig() {
            if(stringConfigs.isEmpty())
                return Boolean.FALSE;
            return Boolean.parseBoolean(stringConfigs.getFirst());
        }
    }

    public static final class ActivationModeConfig
            extends ConfigWithDate<Map<ProductType, ProcessingMode>> {

        public ActivationModeConfig(String configWithDate) {
            super(configWithDate);
        }

        @Override
        public Map<ProductType, ProcessingMode> getConfig() {
            return stringConfigs.stream()
                    .map(pm -> pm.split(":"))
                    .collect(Collectors.toUnmodifiableMap(
                            pm -> ProductType.fromValue(pm[0]),
                            pm -> ProcessingMode.valueOf(pm[1])
                    ));
        }
    }

    private final List<ListStringConfig> requiredAttachmentsRefinementStock890Configs;
    private final List<ListStringConfig> sendOcrAttachmentsRefinementStock890Configs;
    private final List<ListStringConfig> sendOcrAttachmentsFinalValidationStock890Configs;
    private final List<ListStringConfig> sendOcrAttachmentsFinalValidationConfigs;
    private final List<BooleanConfig> strictFinalValidationStock890Config;
    private final List<ActivationModeConfig> productsProcessingModesConfig;
    private final PnPaperTrackerConfigs cfg;

    public TrackerConfigUtils(PnPaperTrackerConfigs cfg) {
        this.requiredAttachmentsRefinementStock890Configs = buildListStringConfig(cfg.getRequiredAttachmentsRefinementStock890());
        this.sendOcrAttachmentsRefinementStock890Configs = buildListStringConfig(cfg.getSendOcrAttachmentsRefinementStock890());
        this.sendOcrAttachmentsFinalValidationConfigs = buildListStringConfig(cfg.getSendOcrAttachmentsFinalValidation());
        this.sendOcrAttachmentsFinalValidationStock890Configs = buildListStringConfig(cfg.getSendOcrAttachmentsFinalValidationStock890());
        this.strictFinalValidationStock890Config = buildListBooleanConfig(cfg.getStrictFinalValidationStock890());
        this.productsProcessingModesConfig = buildListActivationModeConfig(cfg.getProductsProcessingModes());
        this.cfg = cfg;
    }

    public List<ListStringConfig> buildListStringConfig(List<String> configList) {
        return Optional.ofNullable(configList)
                .orElse(List.of())
                .stream()
                .map(ListStringConfig::new)
                .toList();
    }

    public List<BooleanConfig> buildListBooleanConfig(List<String> configList) {
        return Optional.ofNullable(configList)
                .orElse(List.of())
                .stream()
                .map(BooleanConfig::new)
                .toList();
    }

    public List<ActivationModeConfig> buildListActivationModeConfig(List<String> configList) {
        return Optional.ofNullable(configList)
                .orElse(List.of())
                .stream()
                .map(ActivationModeConfig::new)
                .toList();
    }

    public <T> T getActualConfig(
            List<? extends ConfigWithDate<T>> configs,
            LocalDate date,
            String configName
    ) {
        return configs.stream()
                .sorted(Comparator.comparing(
                        (ConfigWithDate<T> c) -> c.getStartConfigurationTime()
                ).reversed())
                .filter(c -> !date.isBefore(c.getStartConfigurationTime()))
                .findFirst()
                .map(ConfigWithDate::getConfig)
                .orElseThrow(() -> new ConfigNotFound(
                        CONFIG_ERROR_CODE,
                        configName + " not found for date: " + date
                ));
    }

    public List<String> getActualRequiredAttachmentsRefinementStock890(LocalDate date) {
        return getActualConfig(
                requiredAttachmentsRefinementStock890Configs,
                date,
                "RequiredAttachmentsRefinementStock890"
        );
    }

    public List<String> getActualSendOcrAttachmentsRefinementStock890(LocalDate date) {
        return getActualConfig(
                sendOcrAttachmentsRefinementStock890Configs,
                date,
                "SendOcrAttachmentsRefinementStock890"
        );
    }

    public List<String> getActualSendOcrAttachmentsFinalValidationStock890(LocalDate date) {
        return getActualConfig(
                sendOcrAttachmentsFinalValidationStock890Configs,
                date,
                "SendOcrAttachmentsFinalValidationStock890"
        );
    }

    public List<String> getActualSendOcrAttachmentsFinalValidation(LocalDate date) {
        return getActualConfig(
                sendOcrAttachmentsFinalValidationConfigs,
                date,
                "SendOcrAttachmentsFinalValidation"
        );
    }

    public Boolean getActualStrictFinalValidationStock890(LocalDate date) {
        return getActualConfig(
                strictFinalValidationStock890Config,
                date,
                "StrictFinalValidationStock890"
        );
    }

    public Map<ProductType, ProcessingMode> getActualProductsProcessingModes(LocalDate date) {
        return getActualConfig(
                productsProcessingModesConfig,
                date,
                "ProductsProcessingModes"
        );
    }

    public Map<ProductType, OcrStatusEnum> getEnableOcrValidationFor() {
        return cfg.getEnableOcrValidationFor().stream()
                .map(config -> {
                    String[] splittedConfig = config.split(":");
                    return Map.entry(ProductType.fromValue(splittedConfig[0]), splittedConfig[1]);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> OcrStatusEnum.valueOf(entry.getValue())));
    }
}