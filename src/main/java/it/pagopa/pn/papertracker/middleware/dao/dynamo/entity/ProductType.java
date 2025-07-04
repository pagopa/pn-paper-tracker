package it.pagopa.pn.papertracker.middleware.dao.dynamo.entity;

public enum ProductType {

    RS("RS"),

    AR("AR"),

    RACCOMANDATA_890("890"),

    RIR("RIR"),

    RIS("RIS");

    private final String value;

    ProductType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
