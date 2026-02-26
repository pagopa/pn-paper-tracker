package it.pagopa.pn.papertracker.config;

import it.pagopa.pn.papertracker.exception.ConfigNotFound;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProcessingMode;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.model.OcrStatusEnum;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TrackerConfigUtils {

    private static final String SEPARATOR = ";";
    private static final String CONFIG_ERROR_CODE = "REQUIRED_CONFIG_NOT_FOUND";
    private static final String OCR_FILTER_DISABLED = "DISABLED";


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
            if (stringConfigs.isEmpty())
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

    public record OcrFilterUnifiedDeliveryDriverConfigRecord(List<String> drivers, boolean isDisabled) {
        public OcrFilterUnifiedDeliveryDriverConfigRecord(List<String> drivers) {
            this(drivers, drivers.contains(OCR_FILTER_DISABLED));
        }
    }

    public static final class CronTemporalConfig {

        private CronExpression cronExpression;
        private boolean isValidConfig;
        private boolean isDisabled;

        public CronTemporalConfig(String config) {
            initConfig(config);
        }

        private void initConfig(String config) {
            if (!StringUtils.hasText(config)) {
                log.info("Cron configuration is empty or null");
                isValidConfig = false;
                return;
            }
            if (OCR_FILTER_DISABLED.equalsIgnoreCase(config)) {
                log.info("Cron configuration is set to DISABLED");
                isDisabled = true;
                isValidConfig = true;
                return;
            }
            try {
                cronExpression = CronExpression.parse(config);
                log.info("Parsed cron expression: {}", this.cronExpression);
                isValidConfig = true;
                isDisabled = false;
            } catch (IllegalArgumentException e) {
                log.error("Invalid cron expression: {}", config, e);
                isValidConfig = false;
            }
        }

        public boolean isActive(Instant now) {
            if (!isValidConfig || isDisabled) {
                return false;
            }

            ZonedDateTime zdtNow = now.atZone(ZoneId.of("Europe/Rome"));
            log.info("Current time in ZonedDateTime: {}", zdtNow);

            // Sottraiamo un secondo per includere l'istante attuale
            ZonedDateTime justBefore = zdtNow.minusSeconds(1);

            ZonedDateTime nextExecution = cronExpression.next(zdtNow); //Lasciata per loggare la prossima esecuzione corretta
            log.info("Next execution of cron: {}", nextExecution);
            nextExecution = cronExpression.next(justBefore);

            return Objects.nonNull(nextExecution) && zdtNow.isEqual(nextExecution);
        }
    }

    private final List<ListStringConfig> requiredAttachmentsRefinementStock890Configs;
    private final List<ListStringConfig> sendOcrAttachmentsRefinementStock890Configs;
    private final List<ListStringConfig> sendOcrAttachmentsFinalValidationStock890Configs;
    private final List<ListStringConfig> sendOcrAttachmentsFinalValidationConfigs;
    private final List<BooleanConfig> strictFinalValidationStock890Config;
    private final List<ActivationModeConfig> productsProcessingModesConfig;
    private final CronTemporalConfig ocrFilterTemporalConfig;
    private final OcrFilterUnifiedDeliveryDriverConfigRecord ocrFilterUnifiedDeliveryDriverConfig;
    private final PnPaperTrackerConfigs cfg;

    public TrackerConfigUtils(PnPaperTrackerConfigs cfg) {
        this.requiredAttachmentsRefinementStock890Configs = buildListStringConfig(cfg.getRequiredAttachmentsRefinementStock890());
        this.sendOcrAttachmentsRefinementStock890Configs = buildListStringConfig(cfg.getSendOcrAttachmentsRefinementStock890());
        this.sendOcrAttachmentsFinalValidationConfigs = buildListStringConfig(cfg.getSendOcrAttachmentsFinalValidation());
        this.sendOcrAttachmentsFinalValidationStock890Configs = buildListStringConfig(cfg.getSendOcrAttachmentsFinalValidationStock890());
        this.strictFinalValidationStock890Config = buildListBooleanConfig(cfg.getStrictFinalValidationStock890());
        this.productsProcessingModesConfig = buildListActivationModeConfig(cfg.getProductsProcessingModes());
        this.ocrFilterTemporalConfig = buildOcrFilterTemporalConfig(cfg.getOcrFilterTemporal());
        this.ocrFilterUnifiedDeliveryDriverConfig = new OcrFilterUnifiedDeliveryDriverConfigRecord(cfg.getOcrFilterUnifiedDeliveryDriver());
        this.cfg = cfg;
    }

    @PostConstruct
    public void init() {
        log.info("TrackerConfigUtils PostConstruct initialization started");
        checkIfOcrFilterTemporalIsValid();
        checkIfOcrFilterDriverIsValid();
    }

    private void checkIfOcrFilterTemporalIsValid() {
        if (!ocrFilterTemporalConfig.isValidConfig) {
            throw new ConfigNotFound(
                    CONFIG_ERROR_CODE,
                    "OcrFilterTemporal config not found");
        }
    }

    private void checkIfOcrFilterDriverIsValid() {
        if (ocrFilterUnifiedDeliveryDriverConfig.drivers().isEmpty()) {
            throw new ConfigNotFound(
                    CONFIG_ERROR_CODE,
                    "OcrFilterUnifiedDeliveryDriver config not found");
        }
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

    public CronTemporalConfig buildOcrFilterTemporalConfig(String config) {
        return new CronTemporalConfig(config);
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

    public boolean isOcrFilterTemporalActive(Instant now) {
        return ocrFilterTemporalConfig.isActive(now);
    }

    public boolean isOcrFilterTemporalDisabled() {
        return ocrFilterTemporalConfig.isDisabled;
    }

    public boolean isOcrFilterDriverActive(String unifiedDeliveryDriver) {
        return ocrFilterUnifiedDeliveryDriverConfig.drivers().contains(unifiedDeliveryDriver);
    }

    public boolean isOcrFilterDriverDisabled() {
        return ocrFilterUnifiedDeliveryDriverConfig.isDisabled();
    }

}