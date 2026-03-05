package com.zeus.upload.sql;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.util.StringUtils;

public class MergeSqlBuilder {

    private final SqlDialect sqlDialect;

    public MergeSqlBuilder(SqlDialect sqlDialect) {
        this.sqlDialect = sqlDialect;
    }

    public String buildDb2MergeSql(
            String library,
            String table,
            List<String> insertColumns,
            List<String> updateColumns,
            List<String> keyColumns
    ) {
        List<String> safeInsertColumns = normalizeColumns(insertColumns);
        List<String> safeUpdateColumns = normalizeColumns(updateColumns);
        List<String> safeKeyColumns = normalizeColumns(keyColumns);

        if (!StringUtils.hasText(library) || !StringUtils.hasText(table)) {
            throw new IllegalArgumentException("Library and table are required for MERGE.");
        }
        if (safeInsertColumns.isEmpty()) {
            throw new IllegalArgumentException("At least one insert column is required for MERGE.");
        }
        if (safeKeyColumns.isEmpty()) {
            throw new IllegalArgumentException("At least one key column is required for MERGE.");
        }
        if (safeUpdateColumns.isEmpty()) {
            throw new IllegalArgumentException("At least one non-key update column is required for MERGE.");
        }

        Set<String> insertColumnSet = new LinkedHashSet<>(safeInsertColumns);
        if (insertColumnSet.size() != safeInsertColumns.size()) {
            throw new IllegalArgumentException("Insert columns must be unique.");
        }
        if (!insertColumnSet.containsAll(safeKeyColumns)) {
            throw new IllegalArgumentException("All key columns must be part of insert columns.");
        }
        if (!insertColumnSet.containsAll(safeUpdateColumns)) {
            throw new IllegalArgumentException("All update columns must be part of insert columns.");
        }

        String qualifiedTable = sqlDialect.qualifyTable(library, table);
        String valuePlaceholders = String.join(", ", safeInsertColumns.stream().map(c -> "?").toList());
        String sourceColumns = String.join(", ", safeInsertColumns.stream().map(sqlDialect::quoteIdentifier).toList());
        String onClause = String.join(" AND ", safeKeyColumns.stream()
                .map(column -> "T." + sqlDialect.quoteIdentifier(column) + " = S." + sqlDialect.quoteIdentifier(column))
                .toList());
        String updateClause = String.join(", ", safeUpdateColumns.stream()
                .map(column -> "T." + sqlDialect.quoteIdentifier(column) + " = S." + sqlDialect.quoteIdentifier(column))
                .toList());
        String insertColumnsClause = String.join(", ", safeInsertColumns.stream().map(sqlDialect::quoteIdentifier).toList());
        String insertValuesClause = String.join(", ", safeInsertColumns.stream()
                .map(column -> "S." + sqlDialect.quoteIdentifier(column))
                .toList());

        return "MERGE INTO " + qualifiedTable + " AS T "
                + "USING (VALUES (" + valuePlaceholders + ")) AS S(" + sourceColumns + ") "
                + "ON (" + onClause + ") "
                + "WHEN MATCHED THEN UPDATE SET " + updateClause + " "
                + "WHEN NOT MATCHED THEN INSERT (" + insertColumnsClause + ") VALUES (" + insertValuesClause + ")";
    }

    private List<String> normalizeColumns(List<String> columns) {
        List<String> normalized = new ArrayList<>();
        if (columns == null) {
            return normalized;
        }
        for (String column : columns) {
            if (StringUtils.hasText(column)) {
                normalized.add(column.trim());
            }
        }
        return normalized;
    }
}
