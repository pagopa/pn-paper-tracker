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
    UNKNOWN;

    public static DeliveryFailureCauseEnum fromValue(String deliveryFailureCause) {
        return Stream.of(values())
                .filter(value -> value.name().equalsIgnoreCase(deliveryFailureCause))
                .findFirst()
                .orElse(UNKNOWN);
    }
}