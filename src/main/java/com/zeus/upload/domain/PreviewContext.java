package com.zeus.upload.domain;

import java.util.ArrayList;
import java.util.List;

public class PreviewContext {

    private ParsedCsv parsedCsv;
    private String originalFilename;
    private boolean useExistingTable;
    private boolean upsertEnabled;
    private String existingTableName;
    private List<DbColumnMeta> dbColumns = new ArrayList<>();
    private List<ColumnMapping> mappings = new ArrayList<>();
    private List<String> keyColumns = new ArrayList<>();

    public ParsedCsv getParsedCsv() {
        return parsedCsv;
    }

    public void setParsedCsv(ParsedCsv parsedCsv) {
        this.parsedCsv = parsedCsv;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public boolean isUseExistingTable() {
        return useExistingTable;
    }

    public void setUseExistingTable(boolean useExistingTable) {
        this.useExistingTable = useExistingTable;
    }

    public String getExistingTableName() {
        return existingTableName;
    }

    public void setExistingTableName(String existingTableName) {
        this.existingTableName = existingTableName;
    }

    public boolean isUpsertEnabled() {
        return upsertEnabled;
    }

    public void setUpsertEnabled(boolean upsertEnabled) {
        this.upsertEnabled = upsertEnabled;
    }

    public List<DbColumnMeta> getDbColumns() {
        return dbColumns;
    }

    public void setDbColumns(List<DbColumnMeta> dbColumns) {
        this.dbColumns = dbColumns;
    }

    public List<ColumnMapping> getMappings() {
        return mappings;
    }

    public void setMappings(List<ColumnMapping> mappings) {
        this.mappings = mappings;
    }

    public List<String> getKeyColumns() {
        return keyColumns;
    }

    public void setKeyColumns(List<String> keyColumns) {
        this.keyColumns = keyColumns == null ? new ArrayList<>() : new ArrayList<>(keyColumns);
    }
}
