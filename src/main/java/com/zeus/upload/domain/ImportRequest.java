package com.zeus.upload.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

public class ImportRequest {

    @NotBlank
    private String library;

    @NotBlank
    private String tableName;

    private boolean dropAndRecreate;
    private boolean useExistingTable;
    private String existingTableName;

    @Valid
    private List<ColumnProposal> columns = new ArrayList<>();

    public String getLibrary() {
        return library;
    }

    public void setLibrary(String library) {
        this.library = library;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public boolean isDropAndRecreate() {
        return dropAndRecreate;
    }

    public void setDropAndRecreate(boolean dropAndRecreate) {
        this.dropAndRecreate = dropAndRecreate;
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

    public List<ColumnProposal> getColumns() {
        return columns;
    }

    public void setColumns(List<ColumnProposal> columns) {
        this.columns = columns;
    }
}
