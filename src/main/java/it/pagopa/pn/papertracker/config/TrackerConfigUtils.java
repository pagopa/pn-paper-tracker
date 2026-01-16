package it.pagopa.pn.papertracker.config;

import it.pagopa.pn.papertracker.exception.ConfigNotFound;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProcessingMode;
import it.pagopa.pn.papertracker.middleware.dao.dynamo.entity.ProductType;
import it.pagopa.pn.papertracker.model.OcrStatusEnum;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class TrackerConfigUtils {

    private static final String SEPARATOR = ";";
    private static final int START_DATE_INDEX = 0;
    private static final String CONFIG_ERROR_CODE = "REQUIRED_CONFIG_NOT_FOUND";

    public record AttachmentsConfig(LocalDate startConfigurationTime, List<String> documentTypes) {}
    public record StrictValidationConfig(LocalDate startConfigurationTime, Boolean enableStrictValidation) {}
    public record ActivationModeConfig(LocalDate startConfigurationTime, Map<ProductType, ProcessingMode> activationMode) {}

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
        this.strictFinalValidationStock890Config = buildSortedList(cfg.getStrictFinalValidationStock890(), this::toStrictValidationConfig);
        this.productsProcessingModesConfig = buildSortedList(cfg.getProductsProcessingModes(), this::toActivationModeConfig);
        this.cfg = cfg;
    }

    private <T> List<T> buildSortedList(List<String> source, Function<String, T> mapper) {
        if (CollectionUtils.isEmpty(source)) {
            return Collections.emptyList();
        }
        return source.stream()
                .map(mapper)
                .sorted(Comparator.comparing(this::extractStartDate).reversed())
                .toList();
    }

    private LocalDate extractStartDate(Object config) {
        if (config instanceof AttachmentsConfig c) return c.startConfigurationTime();
        if (config instanceof StrictValidationConfig c) return c.startConfigurationTime();
        if (config instanceof ActivationModeConfig c) return c.startConfigurationTime();
        throw new IllegalArgumentException("Unknown config type");
    }

    private List<AttachmentsConfig> buildAttachmentsConfigFromStringList(List<String> attachmentsConfigsStr) {
        return buildSortedList(attachmentsConfigsStr, this::toAttachmentsConfig);
    }

    private AttachmentsConfig toAttachmentsConfig(String config) {
        String[] split = config.split(SEPARATOR);
        LocalDate startDate = LocalDate.parse(split[START_DATE_INDEX]);
        List<String> documentTypes = Arrays.asList(Arrays.copyOfRange(split, 1, split.length));
        return new AttachmentsConfig(startDate, documentTypes);
    }

    private StrictValidationConfig toStrictValidationConfig(String config) {
        String[] split = config.split(SEPARATOR);
        LocalDate startDate = LocalDate.parse(split[START_DATE_INDEX]);
        Boolean enabled = split.length > 1 && Boolean.parseBoolean(split[1]);
        return new StrictValidationConfig(startDate, enabled);
    }

    private ActivationModeConfig toActivationModeConfig(String config) {
        String[] split = config.split(SEPARATOR);
        LocalDate startDate = LocalDate.parse(split[START_DATE_INDEX]);

        Map<ProductType, ProcessingMode> activationModeMap =
                Arrays.stream(split, 1, split.length)
                        .map(pm -> pm.split(":"))
                        .collect(Collectors.toMap(
                                pm -> ProductType.fromValue(pm[0]),
                                pm -> ProcessingMode.valueOf(pm[1])
                        ));

        return new ActivationModeConfig(startDate, activationModeMap);
    }

    private boolean isStartDateAfterOrEqualToConfigDate(LocalDate startDate, LocalDate configStartDate) {
        return !startDate.isBefore(configStartDate); // negando isBefore ottengo isAfterOrEqual
    }

    private <T, R> R getActualConfig(
            List<T> configs,
            LocalDate startDate,
            Function<T, LocalDate> dateExtractor,
            Function<T, R> valueExtractor,
            String errorMessage
    ) {
        return configs.stream()
                .filter(c -> isStartDateAfterOrEqualToConfigDate(startDate, dateExtractor.apply(c)))
                .findFirst()
                .map(valueExtractor)
                .orElseThrow(() -> new ConfigNotFound(CONFIG_ERROR_CODE, errorMessage + startDate));
    }

    public List<String> getActualRequiredAttachmentsRefinementStock890(LocalDate startDate) {
        return getActualConfig(
                requiredAttachmentsRefinementStock890Configs,
                startDate,
                AttachmentsConfig::startConfigurationTime,
                AttachmentsConfig::documentTypes,
                "RequiredAttachmentsRefinementStock890 not found for date: "
        );
    }

    public List<String> getActualSendOcrAttachmentsRefinementStock890(LocalDate startDate) {
        return getActualConfig(
                sendOcrAttachmentsRefinementStock890Configs,
                startDate,
                AttachmentsConfig::startConfigurationTime,
                AttachmentsConfig::documentTypes,
                "SendOcrAttachmentsRefinementStock890 not found for date: "
        );
    }

    public List<String> getActualSendOcrAttachmentsFinalValidationStock890(LocalDate startDate) {
        return getActualConfig(
                sendOcrAttachmentsFinalValidationStock890Configs,
                startDate,
                AttachmentsConfig::startConfigurationTime,
                AttachmentsConfig::documentTypes,
                "SendOcrAttachmentsFinalValidationStock890 not found for date: "
        );
    }

    public List<String> getActualSendOcrAttachmentsFinalValidationConfigs(LocalDate startDate) {
        return getActualConfig(
                sendOcrAttachmentsFinalValidationConfigs,
                startDate,
                AttachmentsConfig::startConfigurationTime,
                AttachmentsConfig::documentTypes,
                "SendOcrAttachmentsFinalValidation not found for date: "
        );
    }

    public Boolean getActualStrictFinalValidationStock890Config(LocalDate startDate) {
        return strictFinalValidationStock890Config.stream()
                .filter(c -> isStartDateAfterOrEqualToConfigDate(startDate, c.startConfigurationTime()))
                .findFirst()
                .map(StrictValidationConfig::enableStrictValidation)
                .orElse(Boolean.FALSE);
    }

    public Map<ProductType, OcrStatusEnum> getEnableOcrValidationFor() {
        return cfg.getEnableOcrValidationFor().stream()
                .map(config -> config.split(":"))
                .collect(Collectors.toMap(
                        split -> ProductType.fromValue(split[0]),
                        split -> OcrStatusEnum.valueOf(split[1])
                ));
    }

    public Map<ProductType, ProcessingMode> getActualProductsProcessingModesConfig(LocalDate startDate) {
        return getActualConfig(
                productsProcessingModesConfig,
                startDate,
                ActivationModeConfig::startConfigurationTime,
                ActivationModeConfig::activationMode,
                "ProductsProcessingModes not found for date: "
        );
    }
}
