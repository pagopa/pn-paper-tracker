package it.pagopa.pn.papertracker.model.sequence;

import lombok.Getter;

import java.util.Set;
/**
 * Enum that defines required sequences of status codes for various processes.
 * Each enum constant represents a specific sequence and holds a set of strings,
 * where each string specifies a status code in the sequence.
 * The "?" prefix indicates that the status code is optional.
 */
@Getter
public enum RequiredSequenceStatusCodes {

    RECRN005C(Set.of("RECRN010","RECRN011","RECRN005A","RECRN005B","RECRN005C")),
    RECRN004C(Set.of("RECRN010", "RECRN011", "RECRN004A", "RECRN004B", "RECRN004C")),
    RECRN003C(Set.of("RECRN010", "RECRN011", "RECRN003A", "RECRN003B", "RECRN003C")),
    RECRN002F(Set.of("RECRN002D", "RECRN002E", "RECRN002F")),
    RECRN002C(Set.of("RECRN002A", "RECRN002B", "RECRN002C")),
    RECRN001C(Set.of("RECRN001A", "RECRN001B", "RECRN001C")),
    RECRI003C(Set.of("RECRI001", "RECRI002", "RECRI003A", "RECRI003B", "RECRI003C")),
    RECRI004C(Set.of("RECRI001", "RECRI002", "RECRI004A", "RECRI004B", "RECRI004C")),
    RECAG001C(Set.of("RECAG001A", "RECAG001B", "RECAG001C")),
    RECAG002C(Set.of("RECAG002A", "RECAG002B", "RECAG002C")),
    RECAG003C(Set.of("RECAG003A", "RECAG003B", "RECAG003C")),
    RECAG003F(Set.of("RECAG003D", "RECAG003E", "RECAG003F")),
    RECAG005C(Set.of("RECAG010", "RECAG011A", "?RECAG011B", "RECAG005A", "?RECAG005B", "RECAG005C")),
    RECAG006C(Set.of("RECAG010", "RECAG011A", "?RECAG011B", "RECAG006A", "?RECAG006B", "RECAG006C")),
    RECAG007C(Set.of("RECAG010", "RECAG011A", "?RECAG011B", "RECAG007A", "RECAG007B", "RECAG007C")),
    RECAG008C(Set.of("RECAG010", "RECAG011A", "RECAG011B", "RECAG008A", "RECAG008B", "RECAG008C"));

    private final Set<String> statusCodeSequence;

    RequiredSequenceStatusCodes(Set<String> statusCodeSequence) {
        this.statusCodeSequence = statusCodeSequence;
    }


}
