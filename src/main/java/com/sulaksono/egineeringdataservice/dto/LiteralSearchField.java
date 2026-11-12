package com.sulaksono.egineeringdataservice.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum LiteralSearchField {
    CONTENT("content", "content"),
    PATH("path", "path"),
    FILE_NAME("fileName", "file_name"),
    MODULE("module", "module");

    private final String apiName;
    private final String columnName;

    LiteralSearchField(String apiName, String columnName) {
        this.apiName = apiName;
        this.columnName = columnName;
    }

    public String apiName() {
        return apiName;
    }

    public String columnName() {
        return columnName;
    }

    @JsonCreator
    public static LiteralSearchField from(String value) {
        for (LiteralSearchField field : values()) {
            if (field.apiName.equalsIgnoreCase(value) || field.name().equalsIgnoreCase(value)) {
                return field;
            }
        }
        throw new IllegalArgumentException("Unsupported literal search field: " + value);
    }
}
