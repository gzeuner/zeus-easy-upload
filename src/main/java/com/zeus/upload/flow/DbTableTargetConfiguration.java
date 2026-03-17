package com.zeus.upload.flow;

import com.zeus.upload.domain.ColumnMapping;
import com.zeus.upload.domain.ColumnProposal;
import com.zeus.upload.domain.DbColumnMeta;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DbTableTargetConfiguration implements TargetConfiguration {

    private final String library;
    private final String tableName;
    private final DbTableWriteMode writeMode;
    private final boolean dropAndRecreate;
    private final List<ColumnProposal> columns;
    private final List<ColumnMapping> mappings;
    private final List<String> keyColumns;
    private final List<DbColumnMeta> dbColumns;

    public DbTableTargetConfiguration(
            String library,
            String tableName,
            DbTableWriteMode writeMode,
            boolean dropAndRecreate,
            List<ColumnProposal> columns,
            List<ColumnMapping> mappings,
            List<String> keyColumns,
            List<DbColumnMeta> dbColumns
    ) {
        this.library = Objects.requireNonNull(library, "library must not be null");
        this.tableName = Objects.requireNonNull(tableName, "tableName must not be null");
        this.writeMode = Objects.requireNonNull(writeMode, "writeMode must not be null");
        this.dropAndRecreate = dropAndRecreate;
        this.columns = columns == null ? List.of() : List.copyOf(new ArrayList<>(columns));
        this.mappings = mappings == null ? List.of() : List.copyOf(new ArrayList<>(mappings));
        this.keyColumns = keyColumns == null ? List.of() : List.copyOf(new ArrayList<>(keyColumns));
        this.dbColumns = dbColumns == null ? List.of() : List.copyOf(new ArrayList<>(dbColumns));
    }

    public String getLibrary() {
        return library;
    }

    public String getTableName() {
        return tableName;
    }

    public DbTableWriteMode getWriteMode() {
        return writeMode;
    }

    public boolean isDropAndRecreate() {
        return dropAndRecreate;
    }

    public List<ColumnProposal> getColumns() {
        return columns;
    }

    public List<ColumnMapping> getMappings() {
        return mappings;
    }

    public List<String> getKeyColumns() {
        return keyColumns;
    }

    public List<DbColumnMeta> getDbColumns() {
        return dbColumns;
    }
}
