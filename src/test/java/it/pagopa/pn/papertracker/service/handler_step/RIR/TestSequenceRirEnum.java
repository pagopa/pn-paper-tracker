package it.pagopa.pn.papertracker.service.handler_step.RIR;

import lombok.Getter;

import java.util.List;

@Getter
public enum TestSequenceRirEnum {

    OK_RIR(List.of("CON080", "CON020", "RECRI001", "RECRI002", "RECRI003A", "RECRI003B", "RECRI003C"), List.of("RECRI003B-AR", "CON020-7Z")),
    FAIL_RIR(List.of("CON080", "CON020", "RECRI001", "RECRI002", "RECRI004A", "RECRI004B", "RECRI004C"), List.of("CON020-7Z", "RECRI004B-Plico")),
    OK_RETRY_RIR(List.of("CON080", "CON020", "RECRI005", "CON080", "CON020", "RECRI001", "RECRI002", "RECRI003A", "RECRI003B", "RECRI003C"), List.of("RECRI003B-AR", "CON020-7Z"));

    private final List<String> sentDocuments;
    private final List<String> statusCodes;

    TestSequenceRirEnum(List<String> statusCodes, List<String> sentDocuments) {
        this.sentDocuments = sentDocuments;
        this.statusCodes = statusCodes;
    }
}
