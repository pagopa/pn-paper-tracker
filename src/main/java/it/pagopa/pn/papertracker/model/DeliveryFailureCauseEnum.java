package it.pagopa.pn.papertracker.model;

import lombok.Getter;

import java.util.stream.Stream;

@Getter
public enum DeliveryFailureCauseEnum {

    M01,
    M02,
    M03,
    M04,
    M05,
    M06,
    M07,
    M08,
    M09,
    F01,
    F02,
    F03,
    F04,
    UNKNOWN;

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