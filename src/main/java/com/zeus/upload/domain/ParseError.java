package com.zeus.upload.domain;

public class ParseError {

    private long rowNumber;
    private String columnName;
    private String rawValue;
    private String message;

    public ParseError() {
    }

    public ParseError(long rowNumber, String columnName, String rawValue, String message) {
        this.rowNumber = rowNumber;
        this.columnName = columnName;
        this.rawValue = rawValue;
        this.message = message;
    }

    public long getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(long rowNumber) {
        this.rowNumber = rowNumber;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getRawValue() {
        return rawValue;
    }

    public void setRawValue(String rawValue) {
        this.rawValue = rawValue;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
