package it.pagopa.pn.papertracker.model;

import lombok.Getter;

@Getter
public enum FileType {
    PDF("pdf");

    private final String value;

    FileType(String value) {
        this.value = value;
    }

    public static FileType fromValue(String fileType) {
        for (FileType ft : FileType.values()) {
            if (ft.value.equalsIgnoreCase(fileType)) {
                return ft;
            }
        }
        throw new IllegalArgumentException("Unknown file type: " + fileType);
    }
}
