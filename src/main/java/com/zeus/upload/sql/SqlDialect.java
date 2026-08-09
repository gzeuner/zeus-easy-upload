package com.zeus.upload.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Database-specific SQL helpers. Implementations must never embed credentials;
 * they only shape SQL for the active engine.
 */
public interface SqlDialect {

    DatabaseProduct product();

    IdentifierPolicy identifierPolicy();

    String quoteIdentifier(String identifier);

    String qualifyTable(String libraryOrSchema, String table);

    /**
     * Optional DDL to create a schema/library. {@code null} when the engine
     * expects schemas to exist already (IBM i libraries).
     */
    default String createSchemaSql(String libraryOrSchema) {
        return null;
    }

    default boolean supportsSchemaAutoCreate() {
        return createSchemaSql("X") != null;
    }

    /**
     * Normalize a raw identifier according to this dialect's policy
     * (case, charset, empty fallback) without length truncation.
     */
    default String normalizeIdentifier(String raw) {
        IdentifierPolicy policy = identifierPolicy();
        String value = raw == null ? "" : raw.trim();
        if (policy.forceUpperCase()) {
            value = value.toUpperCase(Locale.ROOT);
        }
        value = value.replaceAll("[^A-Za-z0-9_]", "_");
        value = value.replaceAll("_+", "_");
        value = value.replaceAll("^_+", "");
        value = value.replaceAll("_+$", "");
        if (value.isBlank()) {
            value = policy.emptyFallback();
        }
        if (policy.forceUpperCase()) {
            value = value.toUpperCase(Locale.ROOT);
        }
        if (!Character.isLetter(value.charAt(0))) {
            value = policy.leadingDigitPrefix() + value;
            if (policy.forceUpperCase()) {
                value = value.toUpperCase(Locale.ROOT);
            }
        }
        return value;
    }

    /**
     * SQL type fragment for CREATE TABLE column definitions, e.g. {@code VARCHAR(64)}.
     */
    default String columnTypeDefinition(String sqlType, Integer length, Integer precision, Integer scale) {
        String type = sqlType == null ? "VARCHAR" : sqlType.toUpperCase(Locale.ROOT);
        return switch (type) {
            case "INTEGER" -> "INTEGER";
            case "BIGINT" -> "BIGINT";
            case "DATE" -> "DATE";
            case "TIMESTAMP" -> "TIMESTAMP";
            case "DECIMAL" -> {
                int p = precision == null ? 15 : Math.max(1, Math.min(maxDecimalPrecision(), precision));
                int s = scale == null ? 2 : Math.max(0, Math.min(maxDecimalScale(), scale));
                if (s >= p) {
                    s = Math.max(0, p - 1);
                }
                yield "DECIMAL(" + p + "," + s + ")";
            }
            default -> {
                int len = length == null ? 255 : Math.max(1, Math.min(maxVarcharLength(), length));
                yield "VARCHAR(" + len + ")";
            }
        };
    }

    default int maxDecimalPrecision() {
        return 31;
    }

    default int maxDecimalScale() {
        return 31;
    }

    default int maxVarcharLength() {
        return 4000;
    }

    default String buildInsertSql(String libraryOrSchema, String table, List<String> columns) {
        List<String> quoted = quoteColumns(columns);
        String placeholders = quoted.stream().map(c -> "?").collect(Collectors.joining(", "));
        return "INSERT INTO " + qualifyTable(libraryOrSchema, table)
                + " (" + String.join(", ", quoted) + ") VALUES (" + placeholders + ")";
    }

    default String buildUpdateSql(
            String libraryOrSchema,
            String table,
            List<String> updateColumns,
            List<String> keyColumns
    ) {
        String assignments = quoteColumns(updateColumns).stream()
                .map(col -> col + " = ?")
                .collect(Collectors.joining(", "));
        return "UPDATE " + qualifyTable(libraryOrSchema, table)
                + " SET " + assignments
                + " WHERE " + whereEquals(keyColumns);
    }

    default String buildDeleteSql(String libraryOrSchema, String table, List<String> keyColumns) {
        return "DELETE FROM " + qualifyTable(libraryOrSchema, table)
                + " WHERE " + whereEquals(keyColumns);
    }

    /**
     * Build product-specific upsert SQL. Dialects that do not support upsert
     * must throw {@link UnsupportedOperationException} or return strategy
     * {@link UpsertStrategy#UNSUPPORTED} — prefer throwing with a clear message.
     */
    UpsertSql buildUpsertSql(
            String libraryOrSchema,
            String table,
            List<String> insertColumns,
            List<String> updateColumns,
            List<String> keyColumns
    );

    default UpsertStrategy upsertStrategy() {
        return UpsertStrategy.UNSUPPORTED;
    }

    default String whereEquals(List<String> keyColumns) {
        return quoteColumns(keyColumns).stream()
                .map(col -> col + " = ?")
                .collect(Collectors.joining(" AND "));
    }

    default List<String> quoteColumns(List<String> columns) {
        List<String> quoted = new ArrayList<>();
        if (columns == null) {
            return quoted;
        }
        for (String column : columns) {
            if (column != null && !column.isBlank()) {
                quoted.add(quoteIdentifier(column));
            }
        }
        return quoted;
    }
}
