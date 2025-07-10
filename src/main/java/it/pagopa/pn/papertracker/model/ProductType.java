package it.pagopa.pn.papertracker.model;

import lombok.Getter;

@Getter
public enum ProductType {

    RS("RS"),

    AR("AR"),

    _890("890"),

    RIR("RIR"),

    RIS("RIS");

    private final String value;

    ProductType(String value) {
        this.value = value;
    }

}
