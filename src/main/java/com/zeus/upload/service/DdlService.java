package com.zeus.upload.service;

import com.zeus.upload.domain.ColumnProposal;
import com.zeus.upload.util.ColumnNameSanitizer;
import com.zeus.upload.util.Db2IdentifierUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DdlService {

    private final ColumnNameSanitizer columnNameSanitizer;

    public DdlService(ColumnNameSanitizer columnNameSanitizer) {
        this.columnNameSanitizer = columnNameSanitizer;
    }

    public String createTableSql(String library, String table, List<ColumnProposal> columns) {
        String lib = Db2IdentifierUtil.sanitizeIdentifier(library);
        String tbl = Db2IdentifierUtil.sanitizeIdentifier(table);
        List<String> definitions = new ArrayList<>();
        Set<String> used = new HashSet<>();

        for (ColumnProposal column : columns) {
            String finalName = columnNameSanitizer.uniquify(
                    columnNameSanitizer.sanitizeBase(column.getFinalName()),
                    used,
                    ColumnNameSanitizer.MAX_COLUMN_LENGTH
            );
            column.setFinalName(finalName);
            definitions.add(finalName + " " + resolveTypeDefinition(column) + (column.isNullable() ? "" : " NOT NULL"));
        }

        return "CREATE TABLE " + lib + "." + tbl + " (" + String.join(", ", definitions) + ")";
    }

    public String dropTableSql(String library, String table) {
        String lib = Db2IdentifierUtil.sanitizeIdentifier(library);
        String tbl = Db2IdentifierUtil.sanitizeIdentifier(table);
        return "DROP TABLE " + lib + "." + tbl;
    }

    public String insertSql(String library, String table, List<ColumnProposal> columns) {
        String lib = Db2IdentifierUtil.sanitizeIdentifier(library);
        String tbl = Db2IdentifierUtil.sanitizeIdentifier(table);
        List<String> names = columns.stream().map(ColumnProposal::getFinalName).toList();
        String placeholders = String.join(", ", columns.stream().map(c -> "?").toList());
        return "INSERT INTO " + lib + "." + tbl + " (" + String.join(", ", names) + ") VALUES (" + placeholders + ")";
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
