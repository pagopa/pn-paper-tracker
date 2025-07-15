package it.pagopa.pn.papertracker.model;

import lombok.Getter;

import java.util.List;

@Getter
public enum DeliveryFailureCauseEnum {

    M01("M01"),
    M02("M02"),
    M03("M03"),
    M04("M04"),
    M05("M05"),
    M06("M06"),
    M07("M07"),
    M08("M08"),
    M09("M09");

    private final String value;

    DeliveryFailureCauseEnum(String value) {
        this.value = value;
    }

    public static List<DeliveryFailureCauseEnum> getCausesForStatusCodeE() {
        return List.of(M01, M03, M04);
    }

    public static List<DeliveryFailureCauseEnum> getCausesForStatusCodeB() {
        return List.of(M02, M05, M06, M07, M08, M09);
    }

    public static boolean contains(String value) {
        for (DeliveryFailureCauseEnum cause : values()) {
            if (cause.getValue().equals(value)) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsCauseForB(String value) {
        for (DeliveryFailureCauseEnum cause : getCausesForStatusCodeB()) {
            if (cause.getValue().equals(value)) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsCauseForE(String value) {
        for (DeliveryFailureCauseEnum cause : getCausesForStatusCodeE()) {
            if (cause.getValue().equals(value)) {
                return true;
            }
        }
        return false;
    }
}