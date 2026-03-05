package com.zeus.upload.service;

import com.zeus.upload.config.AppProperties;
import com.zeus.upload.domain.ColumnMapping;
import com.zeus.upload.domain.ColumnProposal;
import com.zeus.upload.domain.DbColumnMeta;
import com.zeus.upload.domain.ImportRequest;
import com.zeus.upload.domain.ImportResult;
import com.zeus.upload.domain.ParseError;
import com.zeus.upload.domain.ParsedCsv;
import com.zeus.upload.sql.MergeSqlBuilder;
import com.zeus.upload.sql.SqlDialect;
import java.math.BigDecimal;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private final DataSource dataSource;
    private final DdlService ddlService;
    private final TypeInferenceService typeInferenceService;
    private final AppProperties appProperties;
    private final SqlDialect sqlDialect;
    private final MergeSqlBuilder mergeSqlBuilder;

    public ImportService(
            DataSource dataSource,
            DdlService ddlService,
            TypeInferenceService typeInferenceService,
            AppProperties appProperties,
            SqlDialect sqlDialect
    ) {
        this.dataSource = dataSource;
        this.ddlService = ddlService;
        this.typeInferenceService = typeInferenceService;
        this.appProperties = appProperties;
        this.sqlDialect = sqlDialect;
        this.mergeSqlBuilder = new MergeSqlBuilder(sqlDialect);
    }

    public ImportResult importCsv(ImportRequest request, ParsedCsv parsedCsv) {
        List<ParseError> errors = new ArrayList<>();
        String createSql = ddlService.createTableSql(request.getLibrary(), request.getTableName(), request.getColumns());
        String insertSql = ddlService.insertSql(request.getLibrary(), request.getTableName(), request.getColumns());

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            if (request.isDropAndRecreate()) {
                try (PreparedStatement drop = connection.prepareStatement(
                        ddlService.dropTableSql(request.getLibrary(), request.getTableName()))) {
                    drop.executeUpdate();
                } catch (SQLException ex) {
                    log.info("DROP TABLE ignored: {}", ex.getMessage());
                }
            }

            try (PreparedStatement create = connection.prepareStatement(createSql)) {
                create.executeUpdate();
            }

            int insertedRows = executeInserts(connection, insertSql, request.getColumns(), parsedCsv.getRows(), errors);
            if (!errors.isEmpty()) {
                connection.rollback();
                throw new ImportException("Import aborted due to conversion errors", errors);
            }

            connection.commit();
            return ImportResult.success("Import successful", createSql, insertedRows);
        } catch (ImportException ex) {
            return ImportResult.failure(ex.getMessage(), createSql, ex.getErrors());
        } catch (Exception ex) {
            errors.add(new ParseError(0, "", "", ex.getMessage()));
            return ImportResult.failure("Import failed: " + ex.getMessage(), createSql, errors);
        }
    }

    public ImportResult importIntoExistingTable(
            String library,
            String tableName,
            ParsedCsv csv,
            List<DbColumnMeta> dbColumns,
            List<ColumnMapping> mappings
    ) {
        List<ParseError> errors = new ArrayList<>();
        List<ColumnMapping> effectiveMappings = determineEffectiveMappings(mappings);
        Set<String> knownColumns = toNormalizedColumnSet(dbColumns);
        for (ColumnMapping mapping : effectiveMappings) {
            String targetColumn = mapping.getTargetColumn();
            if (!knownColumns.isEmpty() && !knownColumns.contains(normalizeColumnName(targetColumn))) {
                errors.add(new ParseError(
                        0,
                        targetColumn,
                        "",
                        "Mapped target column does not exist in table metadata."
                ));
            }
        }
        if (effectiveMappings.isEmpty()) {
            return ImportResult.failure(
                    "No mapped columns selected for import.",
                    "",
                    List.of(new ParseError(0, "", "", "Map at least one CSV column to a target column."))
            );
        }
        if (!errors.isEmpty()) {
            return ImportResult.failure("Import aborted due to invalid mappings.", "", errors);
        }

        String insertSql = buildInsertSql(library, tableName, effectiveMappings);

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            int insertedRows = executeExistingTableInserts(connection, insertSql, effectiveMappings, csv.getRows(), errors);
            if (!errors.isEmpty()) {
                connection.rollback();
                throw new ImportException("Import aborted due to insert errors", errors);
            }
            connection.commit();
            return ImportResult.success("Import successful", insertSql, insertedRows);
        } catch (ImportException ex) {
            return ImportResult.failure(ex.getMessage(), insertSql, ex.getErrors());
        } catch (Exception ex) {
            errors.add(new ParseError(0, "", "", ex.getMessage()));
            return ImportResult.failure("Import failed: " + ex.getMessage(), insertSql, errors);
        }
    }

    public ImportResult upsertIntoExistingTable(
            String library,
            String tableName,
            ParsedCsv csv,
            List<DbColumnMeta> dbColumns,
            List<ColumnMapping> mappings,
            List<String> keyColumns
    ) {
        List<ParseError> errors = new ArrayList<>();
        List<ColumnMapping> effectiveMappings = determineEffectiveMappings(mappings);
        Set<String> knownColumns = toNormalizedColumnSet(dbColumns);
        for (ColumnMapping mapping : effectiveMappings) {
            String targetColumn = mapping.getTargetColumn();
            if (!knownColumns.isEmpty() && !knownColumns.contains(normalizeColumnName(targetColumn))) {
                errors.add(new ParseError(
                        0,
                        targetColumn,
                        "",
                        "Mapped target column does not exist in table metadata."
                ));
            }
        }
        if (effectiveMappings.isEmpty()) {
            return ImportResult.failure(
                    "No mapped columns selected for import.",
                    "",
                    List.of(new ParseError(0, "", "", "Map at least one CSV column to a target column."))
            );
        }

        List<String> insertColumns = effectiveMappings.stream()
                .map(ColumnMapping::getTargetColumn)
                .toList();
        List<String> effectiveKeys = normalizeSelectedKeys(keyColumns);
        if (effectiveKeys.isEmpty()) {
            return ImportResult.failure(
                    "Upsert requires at least one key column.",
                    "",
                    List.of(new ParseError(0, "", "", "Select one or more key columns for MERGE upsert."))
            );
        }

        Set<String> insertSet = toNormalizedStringColumnSet(insertColumns);
        for (String key : effectiveKeys) {
            if (!insertSet.contains(normalizeColumnName(key))) {
                errors.add(new ParseError(
                        0,
                        key,
                        "",
                        "Key column must be mapped and not ignored for upsert."
                ));
            }
        }
        if (!errors.isEmpty()) {
            return ImportResult.failure("Import aborted due to invalid upsert mapping.", "", errors);
        }

        List<String> updateColumns = insertColumns.stream()
                .filter(column -> !containsNormalized(effectiveKeys, column))
                .toList();
        if (updateColumns.isEmpty()) {
            return ImportResult.failure(
                    "Upsert requires at least one non-key mapped column for update.",
                    "",
                    List.of(new ParseError(0, "", "", "Map at least one non-key column for upsert updates."))
            );
        }

        String mergeSql;
        try {
            mergeSql = mergeSqlBuilder.buildDb2MergeSql(library, tableName, insertColumns, updateColumns, effectiveKeys);
        } catch (IllegalArgumentException ex) {
            return ImportResult.failure("Import aborted due to invalid upsert configuration.", "",
                    List.of(new ParseError(0, "", "", ex.getMessage())));
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            int processedRows = executeMergeRows(connection, mergeSql, effectiveMappings, insertColumns, csv.getRows(), errors);
            if (!errors.isEmpty()) {
                connection.rollback();
                throw new ImportException("Import aborted due to upsert errors", errors);
            }
            connection.commit();
            return ImportResult.success("Upsert successful", mergeSql, processedRows);
        } catch (ImportException ex) {
            return ImportResult.failure(ex.getMessage(), mergeSql, ex.getErrors());
        } catch (Exception ex) {
            errors.add(new ParseError(0, "", "", ex.getMessage()));
            return ImportResult.failure("Import failed: " + ex.getMessage(), mergeSql, errors);
        }
    }

    String buildInsertSql(String library, String tableName, List<ColumnMapping> mappings) {
        List<String> targetColumns = mappings.stream()
                .map(ColumnMapping::getTargetColumn)
                .map(sqlDialect::quoteIdentifier)
                .toList();
        String placeholders = String.join(", ", mappings.stream().map(m -> "?").toList());
        return "INSERT INTO " + sqlDialect.qualifyTable(library, tableName)
                + " (" + String.join(", ", targetColumns) + ") VALUES (" + placeholders + ")";
    }

    private int executeInserts(
            Connection connection,
            String insertSql,
            List<ColumnProposal> columns,
            List<List<String>> rows,
            List<ParseError> errors
    ) throws SQLException {
        int insertedRows = 0;
        int pendingBatch = 0;
        try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                List<String> row = rows.get(rowIndex);
                boolean rowHasError = false;

                for (int colIndex = 0; colIndex < columns.size(); colIndex++) {
                    ColumnProposal column = columns.get(colIndex);
                    String value = colIndex < row.size() ? row.get(colIndex) : "";
                    try {
                        bindValue(statement, colIndex + 1, column, value);
                    } catch (Exception ex) {
                        errors.add(new ParseError(
                                rowIndex + 2L,
                                column.getFinalName(),
                                value,
                                ex.getMessage()
                        ));
                        rowHasError = true;
                        break;
                    }
                }

                if (rowHasError) {
                    continue;
                }

                statement.addBatch();
                pendingBatch++;
                if (pendingBatch >= appProperties.getBatchSize()) {
                    statement.executeBatch();
                    insertedRows += pendingBatch;
                    pendingBatch = 0;
                }
            }

            if (pendingBatch > 0) {
                statement.executeBatch();
                insertedRows += pendingBatch;
            }
        }
        return insertedRows;
    }

    private int executeExistingTableInserts(
            Connection connection,
            String insertSql,
            List<ColumnMapping> mappings,
            List<List<String>> rows,
            List<ParseError> errors
    ) throws SQLException {
        int insertedRows = 0;
        int batchSize = Math.max(1, appProperties.getBatchSize());
        List<RowBinding> pendingRows = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                List<String> row = rows.get(rowIndex);
                bindMappedRow(statement, mappings, row);
                statement.addBatch();
                pendingRows.add(new RowBinding(rowIndex, row));

                if (pendingRows.size() >= batchSize) {
                    insertedRows += executeBatchWithFallback(statement, connection, insertSql, mappings, pendingRows, errors);
                    pendingRows.clear();
                }
            }

            if (!pendingRows.isEmpty()) {
                insertedRows += executeBatchWithFallback(statement, connection, insertSql, mappings, pendingRows, errors);
            }
        }
        return insertedRows;
    }

    private int executeBatchWithFallback(
            PreparedStatement batchStatement,
            Connection connection,
            String insertSql,
            List<ColumnMapping> mappings,
            List<RowBinding> pendingRows,
            List<ParseError> errors
    ) throws SQLException {
        try {
            batchStatement.executeBatch();
            return pendingRows.size();
        } catch (BatchUpdateException ex) {
            log.warn("Batch insert failed, falling back to row-by-row execution: {}", ex.getMessage());
            return executeRowsIndividually(connection, insertSql, mappings, pendingRows, errors);
        }
    }

    private int executeRowsIndividually(
            Connection connection,
            String insertSql,
            List<ColumnMapping> mappings,
            List<RowBinding> pendingRows,
            List<ParseError> errors
    ) throws SQLException {
        int inserted = 0;
        try (PreparedStatement single = connection.prepareStatement(insertSql)) {
            for (RowBinding pendingRow : pendingRows) {
                try {
                    bindMappedRow(single, mappings, pendingRow.rowValues());
                    single.executeUpdate();
                    inserted++;
                } catch (Exception ex) {
                    String firstTarget = mappings.isEmpty() ? "" : mappings.get(0).getTargetColumn();
                    errors.add(new ParseError(
                            pendingRow.rowIndex() + 2L,
                            firstTarget,
                            "",
                            ex.getMessage()
                    ));
                }
            }
        }
        return inserted;
    }

    private int executeMergeRows(
            Connection connection,
            String mergeSql,
            List<ColumnMapping> mappings,
            List<String> insertColumns,
            List<List<String>> rows,
            List<ParseError> errors
    ) throws SQLException {
        int processedRows = 0;
        Map<String, Integer> csvIndexesByColumn = csvIndexByTargetColumn(mappings);
        String firstTarget = insertColumns.isEmpty() ? "" : insertColumns.get(0);

        try (PreparedStatement statement = connection.prepareStatement(mergeSql)) {
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                List<String> row = rows.get(rowIndex);
                try {
                    bindMergeRow(statement, insertColumns, csvIndexesByColumn, row);
                    statement.executeUpdate();
                    processedRows++;
                } catch (Exception ex) {
                    errors.add(new ParseError(
                            rowIndex + 2L,
                            firstTarget,
                            "",
                            ex.getMessage()
                    ));
                }
            }
        }
        return processedRows;
    }

    private void bindMappedRow(PreparedStatement statement, List<ColumnMapping> mappings, List<String> row) throws SQLException {
        statement.clearParameters();
        for (int mappingIndex = 0; mappingIndex < mappings.size(); mappingIndex++) {
            ColumnMapping mapping = mappings.get(mappingIndex);
            int csvIndex = mapping.getCsvIndex();
            String value = csvIndex < row.size() ? row.get(csvIndex) : null;
            String trimmed = value == null ? null : value.trim();
            if (!StringUtils.hasText(trimmed)) {
                statement.setNull(mappingIndex + 1, Types.VARCHAR);
            } else {
                statement.setString(mappingIndex + 1, trimmed);
            }
        }
    }

    private void bindMergeRow(
            PreparedStatement statement,
            List<String> insertColumns,
            Map<String, Integer> csvIndexesByColumn,
            List<String> row
    ) throws SQLException {
        statement.clearParameters();
        for (int parameterIndex = 0; parameterIndex < insertColumns.size(); parameterIndex++) {
            String insertColumn = insertColumns.get(parameterIndex);
            Integer csvIndex = csvIndexesByColumn.get(normalizeColumnName(insertColumn));
            String value = (csvIndex == null || csvIndex >= row.size()) ? null : row.get(csvIndex);
            String trimmed = value == null ? null : value.trim();
            if (!StringUtils.hasText(trimmed)) {
                statement.setNull(parameterIndex + 1, Types.VARCHAR);
            } else {
                statement.setString(parameterIndex + 1, trimmed);
            }
        }
    }

    private List<ColumnMapping> determineEffectiveMappings(List<ColumnMapping> mappings) {
        List<ColumnMapping> effective = new ArrayList<>();
        if (mappings == null) {
            return effective;
        }
        for (ColumnMapping mapping : mappings) {
            if (mapping == null || mapping.isIgnored() || !StringUtils.hasText(mapping.getTargetColumn())) {
                continue;
            }
            effective.add(mapping);
        }
        effective.sort(Comparator.comparingInt(ColumnMapping::getCsvIndex));
        return effective;
    }

    private List<String> normalizeSelectedKeys(List<String> keyColumns) {
        List<String> normalized = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (keyColumns == null) {
            return normalized;
        }
        for (String key : keyColumns) {
            if (!StringUtils.hasText(key)) {
                continue;
            }
            String trimmed = key.trim();
            String normalizedName = normalizeColumnName(trimmed);
            if (seen.add(normalizedName)) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    private boolean containsNormalized(List<String> values, String candidate) {
        String normalizedCandidate = normalizeColumnName(candidate);
        for (String value : values) {
            if (normalizeColumnName(value).equals(normalizedCandidate)) {
                return true;
            }
        }
        return false;
    }

    private void bindValue(PreparedStatement statement, int parameterIndex, ColumnProposal column, String rawValue)
            throws SQLException {
        String trimmed = rawValue == null ? "" : rawValue.trim();
        if (trimmed.isEmpty()) {
            statement.setNull(parameterIndex, sqlType(column.getSqlType()));
            return;
        }

        String sqlType = column.getSqlType().toUpperCase();
        switch (sqlType) {
            case "INTEGER" -> statement.setInt(parameterIndex, Integer.parseInt(trimmed));
            case "BIGINT" -> statement.setLong(parameterIndex, Long.parseLong(trimmed));
            case "DECIMAL" -> statement.setBigDecimal(parameterIndex, new BigDecimal(trimmed.replace(',', '.')));
            case "DATE" -> {
                LocalDate date = typeInferenceService.parseDate(trimmed);
                if (date == null) {
                    throw new IllegalArgumentException("Invalid date value");
                }
                statement.setDate(parameterIndex, Date.valueOf(date));
            }
            case "TIMESTAMP" -> {
                LocalDateTime timestamp = typeInferenceService.parseTimestamp(trimmed);
                if (timestamp == null) {
                    throw new IllegalArgumentException("Invalid timestamp value");
                }
                statement.setTimestamp(parameterIndex, Timestamp.valueOf(timestamp));
            }
            default -> statement.setString(parameterIndex, trimmed);
        }
    }

    private int sqlType(String type) {
        return switch (type.toUpperCase()) {
            case "INTEGER" -> Types.INTEGER;
            case "BIGINT" -> Types.BIGINT;
            case "DECIMAL" -> Types.DECIMAL;
            case "DATE" -> Types.DATE;
            case "TIMESTAMP" -> Types.TIMESTAMP;
            default -> Types.VARCHAR;
        };
    }

    private record RowBinding(int rowIndex, List<String> rowValues) {
    }

    private Set<String> toNormalizedColumnSet(List<DbColumnMeta> dbColumns) {
        Set<String> names = new HashSet<>();
        if (dbColumns == null) {
            return names;
        }
        for (DbColumnMeta dbColumn : dbColumns) {
            if (dbColumn != null && StringUtils.hasText(dbColumn.getColumnName())) {
                names.add(normalizeColumnName(dbColumn.getColumnName()));
            }
        }
        return names;
    }

    private Set<String> toNormalizedStringColumnSet(List<String> columns) {
        Set<String> names = new HashSet<>();
        if (columns == null) {
            return names;
        }
        for (String column : columns) {
            if (StringUtils.hasText(column)) {
                names.add(normalizeColumnName(column));
            }
        }
        return names;
    }

    private Map<String, Integer> csvIndexByTargetColumn(List<ColumnMapping> mappings) {
        Map<String, Integer> mappingByColumn = new LinkedHashMap<>();
        for (ColumnMapping mapping : mappings) {
            String targetColumn = mapping.getTargetColumn();
            if (!StringUtils.hasText(targetColumn)) {
                continue;
            }
            mappingByColumn.put(normalizeColumnName(targetColumn), mapping.getCsvIndex());
        }
        return mappingByColumn;
    }

    private String normalizeColumnName(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
