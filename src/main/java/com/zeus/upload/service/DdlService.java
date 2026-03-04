package com.zeus.upload.service;

import com.zeus.upload.domain.ColumnProposal;
import com.zeus.upload.sql.SqlDialect;
import com.zeus.upload.util.ColumnNameSanitizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DdlService {

    private final ColumnNameSanitizer columnNameSanitizer;
    private final SqlDialect sqlDialect;

    public DdlService(ColumnNameSanitizer columnNameSanitizer, SqlDialect sqlDialect) {
        this.columnNameSanitizer = columnNameSanitizer;
        this.sqlDialect = sqlDialect;
    }

    public String createTableSql(String library, String table, List<ColumnProposal> columns) {
        List<String> definitions = new ArrayList<>();
        Set<String> used = new HashSet<>();

        for (ColumnProposal column : columns) {
            String finalName = columnNameSanitizer.uniquify(
                    columnNameSanitizer.sanitizeBase(column.getFinalName()),
                    used,
                    ColumnNameSanitizer.MAX_COLUMN_LENGTH
            );
            column.setFinalName(finalName);
            definitions.add(sqlDialect.quoteIdentifier(finalName) + " "
                    + resolveTypeDefinition(column)
                    + (column.isNullable() ? "" : " NOT NULL"));
        }

        return "CREATE TABLE " + sqlDialect.qualifyTable(library, table) + " (" + String.join(", ", definitions) + ")";
    }

    public String dropTableSql(String library, String table) {
        return "DROP TABLE " + sqlDialect.qualifyTable(library, table);
    }

    public String insertSql(String library, String table, List<ColumnProposal> columns) {
        List<String> names = columns.stream()
                .map(ColumnProposal::getFinalName)
                .map(sqlDialect::quoteIdentifier)
                .toList();
        String placeholders = String.join(", ", columns.stream().map(c -> "?").toList());
        return "INSERT INTO " + sqlDialect.qualifyTable(library, table)
                + " (" + String.join(", ", names) + ") VALUES (" + placeholders + ")";
    }

    private String resolveTypeDefinition(ColumnProposal column) {
        String type = column.getSqlType().toUpperCase();
        return switch (type) {
            case "INTEGER" -> "INTEGER";
            case "BIGINT" -> "BIGINT";
            case "DATE" -> "DATE";
            case "TIMESTAMP" -> "TIMESTAMP";
            case "DECIMAL" -> {
                int precision = column.getPrecision() == null ? 15 : Math.max(1, Math.min(31, column.getPrecision()));
                int scale = column.getScale() == null ? 2 : Math.max(0, Math.min(31, column.getScale()));
                if (scale >= precision) {
                    scale = precision - 1;
                }
                yield "DECIMAL(" + precision + "," + scale + ")";
            }
            default -> {
                int length = column.getLength() == null ? 255 : Math.max(1, Math.min(4000, column.getLength()));
                yield "VARCHAR(" + length + ")";
            }
        };
    }
}
