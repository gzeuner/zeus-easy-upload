package com.zeus.upload.sql;

import java.util.List;
import java.util.stream.Collectors;

/**
 * PostgreSQL dialect scaffold. Portable DML is available; upsert uses ON CONFLICT.
 * Runtime driver/DataSource selection per connection profile is a follow-up.
 */
public class PostgresDialect extends AbstractSqlDialect {

    @Override
    public DatabaseProduct product() {
        return DatabaseProduct.POSTGRES;
    }

    @Override
    public IdentifierPolicy identifierPolicy() {
        return IdentifierPolicy.postgres();
    }

    @Override
    public String createSchemaSql(String libraryOrSchema) {
        return "CREATE SCHEMA IF NOT EXISTS " + quoteIdentifier(libraryOrSchema);
    }

    @Override
    public boolean supportsDropIfExists() {
        return true;
    }

    @Override
    public int maxDecimalPrecision() {
        return 38;
    }

    @Override
    public int maxDecimalScale() {
        return 38;
    }

    @Override
    public int maxVarcharLength() {
        return 10_485_760;
    }

    @Override
    public UpsertStrategy upsertStrategy() {
        return UpsertStrategy.POSTGRES_ON_CONFLICT;
    }

    @Override
    public UpsertSql buildUpsertSql(
            String libraryOrSchema,
            String table,
            List<String> insertColumns,
            List<String> updateColumns,
            List<String> keyColumns
    ) {
        if (insertColumns == null || insertColumns.isEmpty()) {
            throw new IllegalArgumentException("At least one insert column is required for upsert.");
        }
        if (keyColumns == null || keyColumns.isEmpty()) {
            throw new IllegalArgumentException("At least one key column is required for upsert.");
        }
        if (updateColumns == null || updateColumns.isEmpty()) {
            throw new IllegalArgumentException("At least one non-key update column is required for upsert.");
        }

        List<String> quotedInsert = quoteColumns(insertColumns);
        List<String> quotedKeys = quoteColumns(keyColumns);
        List<String> quotedUpdates = quoteColumns(updateColumns);
        String placeholders = quotedInsert.stream().map(c -> "?").collect(Collectors.joining(", "));
        String conflict = String.join(", ", quotedKeys);
        String setClause = quotedUpdates.stream()
                .map(col -> col + " = EXCLUDED." + col)
                .collect(Collectors.joining(", "));

        String sql = "INSERT INTO " + qualifyTable(libraryOrSchema, table)
                + " (" + String.join(", ", quotedInsert) + ") VALUES (" + placeholders + ") "
                + "ON CONFLICT (" + conflict + ") DO UPDATE SET " + setClause;
        return new UpsertSql(sql, UpsertStrategy.POSTGRES_ON_CONFLICT);
    }
}
