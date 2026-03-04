package com.zeus.upload.domain;

public class ColumnMapping {

    private String csvColumn;
    private int csvIndex;
    private String targetColumn;
    private boolean ignored;
    private String note;

    public String getCsvColumn() {
        return csvColumn;
    }

    public void setCsvColumn(String csvColumn) {
        this.csvColumn = csvColumn;
    }

    public int getCsvIndex() {
        return csvIndex;
    }

    public void setCsvIndex(int csvIndex) {
        this.csvIndex = csvIndex;
    }

    public String getTargetColumn() {
        return targetColumn;
    }

    public void setTargetColumn(String targetColumn) {
        this.targetColumn = targetColumn;
    }

    public boolean isIgnored() {
        return ignored;
    }

    public void setIgnored(boolean ignored) {
        this.ignored = ignored;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
