package com.zeus.upload.flow;

import java.util.List;
import java.util.Objects;

public class DbTableSourceConfiguration implements SourceConfiguration {

    private final String library;
    private final String tableName;
    private final List<String> columns;
    private final String connectionProfileName;

    public DbTableSourceConfiguration(String library, String tableName, List<String> columns) {
        this(library, tableName, columns, null);
    }

    public DbTableSourceConfiguration(String library, String tableName, List<String> columns, String connectionProfileName) {
        this.library = Objects.requireNonNull(library, "library must not be null");
        this.tableName = Objects.requireNonNull(tableName, "tableName must not be null");
        this.columns = columns == null ? List.of() : List.copyOf(columns);
        this.connectionProfileName = connectionProfileName;
    }

    public String getLibrary() { return library; }
    public String getTableName() { return tableName; }
    public List<String> getColumns() { return columns; }
    public String getConnectionProfileName() { return connectionProfileName; }
}
