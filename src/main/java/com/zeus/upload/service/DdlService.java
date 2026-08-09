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
        int maxLength = sqlDialect.identifierPolicy().maxLength();

        for (ColumnProposal column : columns) {
            String finalName = columnNameSanitizer.uniquify(
                    columnNameSanitizer.sanitizeBase(column.getFinalName(), sqlDialect.identifierPolicy()),
                    used,
                    maxLength
            );
            column.setFinalName(finalName);
            definitions.add(sqlDialect.quoteIdentifier(finalName) + " "
                    + sqlDialect.columnTypeDefinition(
                            column.getSqlType(),
                            column.getLength(),
                            column.getPrecision(),
                            column.getScale())
                    + (column.isNullable() ? "" : " NOT NULL"));
        }

        return "CREATE TABLE " + sqlDialect.qualifyTable(library, table) + " (" + String.join(", ", definitions) + ")";
    }

    public String dropTableSql(String library, String table) {
        return sqlDialect.dropTableSql(library, table);
    }

    public String dropTableIfExistsSql(String library, String table) {
        if (sqlDialect.supportsDropIfExists()) {
            return sqlDialect.dropTableIfExistsSql(library, table);
        }
        return sqlDialect.dropTableSql(library, table);
    }

    public String insertSql(String library, String table, List<ColumnProposal> columns) {
        List<String> names = columns.stream()
                .map(ColumnProposal::getFinalName)
                .toList();
        return sqlDialect.buildInsertSql(library, table, names);
    }
}
