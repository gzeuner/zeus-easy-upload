package com.zeus.upload.service;

import com.zeus.upload.config.AppProperties;
import com.zeus.upload.domain.ColumnMapping;
import com.zeus.upload.domain.ColumnProposal;
import com.zeus.upload.domain.DbColumnMeta;
import com.zeus.upload.domain.ImportRequest;
import com.zeus.upload.domain.ImportResult;
import com.zeus.upload.domain.ParseError;
import com.zeus.upload.domain.ParsedCsv;
import com.zeus.upload.sql.SqlDialect;
import com.zeus.upload.sql.UpsertSql;
import com.zeus.upload.util.ColumnNameSanitizer;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private final DbSessionFactory dbSessionFactory;
    private final ColumnNameSanitizer columnNameSanitizer;
    private final DataSource dataSource;
    private final DdlService ddlService;
    private final TypeInferenceService typeInferenceService;
    private final ValueConversionService valueConversionService;
    private final AppProperties appProperties;
    private final SqlDialect sqlDialect;

    /** Unit-test / simple construction without profile sessions. */
    public ImportService(
            DataSource dataSource,
            DdlService ddlService,
            TypeInferenceService typeInferenceService,
            AppProperties appProperties,
            SqlDialect sqlDialect
    ) {
        this(null, new ColumnNameSanitizer(), dataSource, ddlService, typeInferenceService,
                typeInferenceService == null ? null : new ValueConversionService(typeInferenceService),
                appProperties, sqlDialect);
    }

    @Autowired
    public ImportService(
            DbSessionFactory dbSessionFactory,
            ColumnNameSanitizer columnNameSanitizer,
            DataSource dataSource,
            DdlService ddlService,
            TypeInferenceService typeInferenceService,
            ValueConversionService valueConversionService,
            AppProperties appProperties,
            SqlDialect sqlDialect
    ) {
        this.dbSessionFactory = dbSessionFactory;
        this.columnNameSanitizer = columnNameSanitizer == null ? new ColumnNameSanitizer() : columnNameSanitizer;
        this.dataSource = dataSource;
        this.ddlService = ddlService;
        this.typeInferenceService = typeInferenceService;
        this.valueConversionService = valueConversionService == null && typeInferenceService != null
                ? new ValueConversionService(typeInferenceService)
                : valueConversionService;
        this.appProperties = appProperties;
        this.sqlDialect = sqlDialect;
    }

    public ImportResult importCsv(ImportRequest request, ParsedCsv parsedCsv) {
        try (DbSession session = openSession(request == null ? null : request.getConnectionProfileName())) {
            return doImportCsv(session, request, parsedCsv, request != null && request.isDryRun());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ImportResult.failure(ex.getMessage(), "", List.of(new ParseError(0, "", "", ex.getMessage())));
        }
    }

    private ImportResult doImportCsv(DbSession session, ImportRequest request, ParsedCsv parsedCsv, boolean dryRun) {
        List<ParseError> errors = new ArrayList<>();
        DdlService ddl = ddlFor(session);
        String createSql = ddl.createTableSql(request.getLibrary(), request.getTableName(), request.getColumns());
        String insertSql = ddl.insertSql(request.getLibrary(), request.getTableName(), request.getColumns());

        try (Connection connection = session.dataSource().getConnection()) {
            connection.setAutoCommit(false);

            ensureSchemaExists(connection, session.dialect(), request.getLibrary());

            if (request.isDropAndRecreate()) {
                try (PreparedStatement drop = connection.prepareStatement(
                        ddl.dropTableSql(request.getLibrary(), request.getTableName()))) {
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

            if (dryRun) {
                connection.rollback();
                return ImportResult.success("Dry run successful; transaction rolled back", createSql, insertedRows);
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
        return importIntoExistingTable(null, library, tableName, csv, dbColumns, mappings, false);
    }

    public ImportResult importIntoExistingTable(
            String library,
            String tableName,
            ParsedCsv csv,
            List<DbColumnMeta> dbColumns,
            List<ColumnMapping> mappings,
            boolean dryRun
    ) {
        return importIntoExistingTable(null, library, tableName, csv, dbColumns, mappings, dryRun);
    }

    public ImportResult importIntoExistingTable(
            String connectionProfileName,
            String library,
            String tableName,
            ParsedCsv csv,
            List<DbColumnMeta> dbColumns,
            List<ColumnMapping> mappings,
            boolean dryRun
    ) {
        try (DbSession session = openSession(connectionProfileName)) {
            return doImportIntoExistingTable(session, library, tableName, csv, dbColumns, mappings, dryRun);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ImportResult.failure(ex.getMessage(), "", List.of(new ParseError(0, "", "", ex.getMessage())));
        }
    }

    private ImportResult doImportIntoExistingTable(
            DbSession session,
            String library,
            String tableName,
            ParsedCsv csv,
            List<DbColumnMeta> dbColumns,
            List<ColumnMapping> mappings,
            boolean dryRun
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

        String insertSql = buildInsertSql(session.dialect(), library, tableName, effectiveMappings);
        Map<String, DbColumnMeta> columnsByName = dbColumnMetaByName(dbColumns);

        try (Connection connection = session.dataSource().getConnection()) {
            connection.setAutoCommit(false);
            int insertedRows = executeExistingTableInserts(
                    connection, insertSql, effectiveMappings, columnsByName, csv.getRows(), errors);
            if (!errors.isEmpty()) {
                connection.rollback();
                throw new ImportException("Import aborted due to insert errors", errors);
            }
            if (dryRun) {
                connection.rollback();
                return ImportResult.success("Dry run successful; transaction rolled back", insertSql, insertedRows);
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
        return upsertIntoExistingTable(null, library, tableName, csv, dbColumns, mappings, keyColumns, false);
    }

    public ImportResult updateIntoExistingTable(
            String library,
            String tableName,
            ParsedCsv csv,
            List<DbColumnMeta> dbColumns,
            List<ColumnMapping> mappings,
            List<String> keyColumns,
            boolean dryRun
    ) {
        return updateIntoExistingTable(null, library, tableName, csv, dbColumns, mappings, keyColumns, dryRun);
    }

    public ImportResult updateIntoExistingTable(
            String connectionProfileName,
            String library,
            String tableName,
            ParsedCsv csv,
            List<DbColumnMeta> dbColumns,
            List<ColumnMapping> mappings,
            List<String> keyColumns,
            boolean dryRun
    ) {
        try (DbSession session = openSession(connectionProfileName)) {
            return doUpdateIntoExistingTable(session, library, tableName, csv, dbColumns, mappings, keyColumns, dryRun);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ImportResult.failure(ex.getMessage(), "", List.of(new ParseError(0, "", "", ex.getMessage())));
        }
    }

    private ImportResult doUpdateIntoExistingTable(
            DbSession session,
            String library,
            String tableName,
            ParsedCsv csv,
            List<DbColumnMeta> dbColumns,
            List<ColumnMapping> mappings,
            List<String> keyColumns,
            boolean dryRun
    ) {
        List<ColumnMapping> effectiveMappings = determineEffectiveMappings(mappings);
        List<String> keys = normalizeSelectedKeys(keyColumns);
        String sql = "";
        List<ParseError> errors = validateKeyedOperation(effectiveMappings, dbColumns, keys, "update");
        if (!errors.isEmpty()) {
            return ImportResult.failure("Update aborted due to invalid mapping.", sql, errors);
        }

        List<ColumnMapping> keyMappings = mappingsForKeys(effectiveMappings, keys);
        List<ColumnMapping> updateMappings = effectiveMappings.stream()
                .filter(mapping -> !containsNormalized(keys, mapping.getTargetColumn()))
                .toList();
        if (updateMappings.isEmpty()) {
            return ImportResult.failure("Update requires at least one non-key mapped column.", sql,
                    List.of(new ParseError(0, "", "", "Map at least one non-key column for updates.")));
        }

        sql = buildUpdateSql(session.dialect(), library, tableName, updateMappings, keyMappings);
        try (Connection connection = session.dataSource().getConnection()) {
            connection.setAutoCommit(false);
            int affectedRows = executeKeyedRows(connection, sql, updateMappings, keyMappings, csv.getRows(), errors);
            if (!errors.isEmpty()) {
                connection.rollback();
                throw new ImportException("Import aborted due to update errors", errors);
            }
            if (dryRun) {
                connection.rollback();
                return ImportResult.success("Dry run successful; transaction rolled back", sql, affectedRows);
            }
            connection.commit();
            return ImportResult.success("Update successful", sql, affectedRows);
        } catch (ImportException ex) {
            return ImportResult.failure(ex.getMessage(), sql, ex.getErrors());
        } catch (Exception ex) {
            errors.add(new ParseError(0, "", "", ex.getMessage()));
            return ImportResult.failure("Update failed: " + ex.getMessage(), sql, errors);
        }
    }

    public ImportResult deleteFromExistingTable(
            String library,
            String tableName,
            ParsedCsv csv,
            List<DbColumnMeta> dbColumns,
            List<ColumnMapping> mappings,
            List<String> keyColumns,
            boolean dryRun
    ) {
        return deleteFromExistingTable(null, library, tableName, csv, dbColumns, mappings, keyColumns, dryRun);
    }

    public ImportResult deleteFromExistingTable(
            String connectionProfileName,
            String library,
            String tableName,
            ParsedCsv csv,
            List<DbColumnMeta> dbColumns,
            List<ColumnMapping> mappings,
            List<String> keyColumns,
            boolean dryRun
    ) {
        try (DbSession session = openSession(connectionProfileName)) {
            return doDeleteFromExistingTable(session, library, tableName, csv, dbColumns, mappings, keyColumns, dryRun);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ImportResult.failure(ex.getMessage(), "", List.of(new ParseError(0, "", "", ex.getMessage())));
        }
    }

    private ImportResult doDeleteFromExistingTable(
            DbSession session,
            String library,
            String tableName,
            ParsedCsv csv,
            List<DbColumnMeta> dbColumns,
            List<ColumnMapping> mappings,
            List<String> keyColumns,
            boolean dryRun
    ) {
        List<ColumnMapping> effectiveMappings = determineEffectiveMappings(mappings);
        List<String> keys = normalizeSelectedKeys(keyColumns);
        String sql = "";
        List<ParseError> errors = validateKeyedOperation(effectiveMappings, dbColumns, keys, "delete");
        if (!errors.isEmpty()) {
            return ImportResult.failure("Delete aborted due to invalid mapping.", sql, errors);
        }

        List<ColumnMapping> keyMappings = mappingsForKeys(effectiveMappings, keys);
        sql = buildDeleteSql(session.dialect(), library, tableName, keyMappings);
        try (Connection connection = session.dataSource().getConnection()) {
            connection.setAutoCommit(false);
            int affectedRows = executeKeyedRows(connection, sql, keyMappings, List.of(), csv.getRows(), errors);
            if (!errors.isEmpty()) {
                connection.rollback();
                throw new ImportException("Import aborted due to delete errors", errors);
            }
            if (dryRun) {
                connection.rollback();
                return ImportResult.success("Dry run successful; transaction rolled back", sql, affectedRows);
            }
            connection.commit();
            return ImportResult.success("Delete successful", sql, affectedRows);
        } catch (ImportException ex) {
            return ImportResult.failure(ex.getMessage(), sql, ex.getErrors());
        } catch (Exception ex) {
            errors.add(new ParseError(0, "", "", ex.getMessage()));
            return ImportResult.failure("Delete failed: " + ex.getMessage(), sql, errors);
        }
    }

    private List<ParseError> validateKeyedOperation(
            List<ColumnMapping> mappings,
            List<DbColumnMeta> dbColumns,
            List<String> keys,
            String operation
    ) {
        List<ParseError> errors = new ArrayList<>();
        Set<String> knownColumns = toNormalizedColumnSet(dbColumns);
        Set<String> mappedColumns = mappings.stream()
                .map(ColumnMapping::getTargetColumn)
                .map(this::normalizeColumnName)
                .collect(java.util.stream.Collectors.toSet());
        for (ColumnMapping mapping : mappings) {
            if (!knownColumns.isEmpty() && !knownColumns.contains(normalizeColumnName(mapping.getTargetColumn()))) {
                errors.add(new ParseError(0, mapping.getTargetColumn(), "", "Mapped target column does not exist in table metadata."));
            }
        }
        if (mappings.isEmpty()) {
            errors.add(new ParseError(0, "", "", "Map at least one CSV column to a target column."));
        }
        if (keys.isEmpty()) {
            errors.add(new ParseError(0, "", "", "Select at least one key column for " + operation + "."));
        }
        for (String key : keys) {
            if (!knownColumns.isEmpty() && !knownColumns.contains(normalizeColumnName(key))) {
                errors.add(new ParseError(0, key, "", "Key column does not exist in table metadata."));
            }
            if (!mappedColumns.contains(normalizeColumnName(key))) {
                errors.add(new ParseError(0, key, "", "Key column must be mapped and not ignored."));
            }
        }
        return errors;
    }

    private List<ColumnMapping> mappingsForKeys(List<ColumnMapping> mappings, List<String> keys) {
        return mappings.stream().filter(mapping -> containsNormalized(keys, mapping.getTargetColumn())).toList();
    }

    private String buildUpdateSql(
            SqlDialect dialect,
            String library,
            String tableName,
            List<ColumnMapping> updates,
            List<ColumnMapping> keys
    ) {
        return dialect.buildUpdateSql(
                library,
                tableName,
                updates.stream().map(ColumnMapping::getTargetColumn).toList(),
                keys.stream().map(ColumnMapping::getTargetColumn).toList()
        );
    }

    private String buildDeleteSql(SqlDialect dialect, String library, String tableName, List<ColumnMapping> keys) {
        return dialect.buildDeleteSql(
                library,
                tableName,
                keys.stream().map(ColumnMapping::getTargetColumn).toList()
        );
    }

    private int executeKeyedRows(
            Connection connection,
            String sql,
            List<ColumnMapping> valueMappings,
            List<ColumnMapping> keyMappings,
            List<List<String>> rows,
            List<ParseError> errors
    ) throws SQLException {
        int affectedRows = 0;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                try {
                    statement.clearParameters();
                    bindMappings(statement, valueMappings, rows.get(rowIndex), 1);
                    bindMappings(statement, keyMappings, rows.get(rowIndex), valueMappings.size() + 1);
                    affectedRows += statement.executeUpdate();
                } catch (Exception ex) {
                    errors.add(new ParseError(rowIndex + 2L, "", "", ex.getMessage()));
                }
            }
        }
        return affectedRows;
    }

    private void bindMappings(PreparedStatement statement, List<ColumnMapping> mappings, List<String> row, int startIndex)
            throws SQLException {
        for (int index = 0; index < mappings.size(); index++) {
            int csvIndex = mappings.get(index).getCsvIndex();
            String value = csvIndex < row.size() ? row.get(csvIndex) : null;
            if (!StringUtils.hasText(value)) {
                statement.setNull(startIndex + index, Types.VARCHAR);
            } else {
                statement.setString(startIndex + index, value.trim());
            }
        }
    }

    public ImportResult upsertIntoExistingTable(
            String library,
            String tableName,
            ParsedCsv csv,
            List<DbColumnMeta> dbColumns,
            List<ColumnMapping> mappings,
            List<String> keyColumns,
            boolean dryRun
    ) {
        return upsertIntoExistingTable(null, library, tableName, csv, dbColumns, mappings, keyColumns, dryRun);
    }

    public ImportResult upsertIntoExistingTable(
            String connectionProfileName,
            String library,
            String tableName,
            ParsedCsv csv,
            List<DbColumnMeta> dbColumns,
            List<ColumnMapping> mappings,
            List<String> keyColumns,
            boolean dryRun
    ) {
        try (DbSession session = openSession(connectionProfileName)) {
            return doUpsertIntoExistingTable(session, library, tableName, csv, dbColumns, mappings, keyColumns, dryRun);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ImportResult.failure(ex.getMessage(), "", List.of(new ParseError(0, "", "", ex.getMessage())));
        }
    }

    private ImportResult doUpsertIntoExistingTable(
            DbSession session,
            String library,
            String tableName,
            ParsedCsv csv,
            List<DbColumnMeta> dbColumns,
            List<ColumnMapping> mappings,
            List<String> keyColumns,
            boolean dryRun
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

        String mergeSql = "";
        try {
            UpsertSql upsertSql = session.dialect().buildUpsertSql(
                    library, tableName, insertColumns, updateColumns, effectiveKeys);
            mergeSql = upsertSql.sql();
        } catch (IllegalArgumentException | UnsupportedOperationException ex) {
            return ImportResult.failure("Import aborted due to invalid upsert configuration.", "",
                    List.of(new ParseError(0, "", "", ex.getMessage())));
        }

        try (Connection connection = session.dataSource().getConnection()) {
            connection.setAutoCommit(false);
            int processedRows = executeMergeRows(connection, mergeSql, effectiveMappings, insertColumns, csv.getRows(), errors);
            if (!errors.isEmpty()) {
                connection.rollback();
                throw new ImportException("Import aborted due to upsert errors", errors);
            }
            if (dryRun) {
                connection.rollback();
                return ImportResult.success("Dry run successful; transaction rolled back", mergeSql, processedRows);
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
        return buildInsertSql(sqlDialect, library, tableName, mappings);
    }

    String buildInsertSql(SqlDialect dialect, String library, String tableName, List<ColumnMapping> mappings) {
        List<String> targetColumns = mappings.stream()
                .map(ColumnMapping::getTargetColumn)
                .toList();
        return dialect.buildInsertSql(library, tableName, targetColumns);
    }

    private DbSession openSession(String connectionProfileName) {
        if (dbSessionFactory != null) {
            return dbSessionFactory.open(connectionProfileName);
        }
        return new DbSession(dataSource, sqlDialect, null, sqlDialect.product(), null);
    }

    private DdlService ddlFor(DbSession session) {
        if (session.isBootstrap() && ddlService != null && session.dialect() == sqlDialect) {
            return ddlService;
        }
        return new DdlService(columnNameSanitizer, session.dialect());
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
            Map<String, DbColumnMeta> columnsByName,
            List<List<String>> rows,
            List<ParseError> errors
    ) throws SQLException {
        int insertedRows = 0;
        int batchSize = Math.max(1, appProperties.getBatchSize());
        List<RowBinding> pendingRows = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                List<String> row = rows.get(rowIndex);
                try {
                    bindMappedRow(statement, mappings, columnsByName, row);
                } catch (Exception ex) {
                    errors.add(new ParseError(rowIndex + 2L, "", "", ex.getMessage()));
                    continue;
                }
                statement.addBatch();
                pendingRows.add(new RowBinding(rowIndex, row));

                if (pendingRows.size() >= batchSize) {
                    insertedRows += executeBatchWithFallback(
                            statement, connection, insertSql, mappings, columnsByName, pendingRows, errors);
                    pendingRows.clear();
                }
            }

            if (!pendingRows.isEmpty()) {
                insertedRows += executeBatchWithFallback(
                        statement, connection, insertSql, mappings, columnsByName, pendingRows, errors);
            }
        }
        return insertedRows;
    }

    private int executeBatchWithFallback(
            PreparedStatement batchStatement,
            Connection connection,
            String insertSql,
            List<ColumnMapping> mappings,
            Map<String, DbColumnMeta> columnsByName,
            List<RowBinding> pendingRows,
            List<ParseError> errors
    ) throws SQLException {
        try {
            batchStatement.executeBatch();
            return pendingRows.size();
        } catch (BatchUpdateException ex) {
            log.warn("Batch insert failed, falling back to row-by-row execution: {}", ex.getMessage());
            return executeRowsIndividually(connection, insertSql, mappings, columnsByName, pendingRows, errors);
        }
    }

    private int executeRowsIndividually(
            Connection connection,
            String insertSql,
            List<ColumnMapping> mappings,
            Map<String, DbColumnMeta> columnsByName,
            List<RowBinding> pendingRows,
            List<ParseError> errors
    ) throws SQLException {
        int inserted = 0;
        try (PreparedStatement single = connection.prepareStatement(insertSql)) {
            for (RowBinding pendingRow : pendingRows) {
                try {
                    bindMappedRow(single, mappings, columnsByName, pendingRow.rowValues());
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

    private void bindMappedRow(
            PreparedStatement statement,
            List<ColumnMapping> mappings,
            Map<String, DbColumnMeta> columnsByName,
            List<String> row
    ) throws SQLException {
        statement.clearParameters();
        for (int mappingIndex = 0; mappingIndex < mappings.size(); mappingIndex++) {
            ColumnMapping mapping = mappings.get(mappingIndex);
            int csvIndex = mapping.getCsvIndex();
            String value = csvIndex < row.size() ? row.get(csvIndex) : null;
            DbColumnMeta meta = columnsByName == null
                    ? null
                    : columnsByName.get(normalizeColumnName(mapping.getTargetColumn()));
            ValueConversionService.SqlTypeFamily family = valueConversionService == null
                    ? ValueConversionService.SqlTypeFamily.VARCHAR
                    : valueConversionService.familyFrom(
                            meta == null ? null : meta.getTypeName(),
                            meta == null ? null : meta.getJdbcType());
            if (valueConversionService != null) {
                valueConversionService.bind(statement, mappingIndex + 1, value, family);
            } else {
                String trimmed = value == null ? null : value.trim();
                if (!StringUtils.hasText(trimmed)) {
                    statement.setNull(mappingIndex + 1, Types.VARCHAR);
                } else {
                    statement.setString(mappingIndex + 1, trimmed);
                }
            }
        }
    }

    private Map<String, DbColumnMeta> dbColumnMetaByName(List<DbColumnMeta> dbColumns) {
        Map<String, DbColumnMeta> map = new LinkedHashMap<>();
        if (dbColumns == null) {
            return map;
        }
        for (DbColumnMeta column : dbColumns) {
            if (column != null && StringUtils.hasText(column.getColumnName())) {
                map.put(normalizeColumnName(column.getColumnName()), column);
            }
        }
        return map;
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
        ValueConversionService.SqlTypeFamily family = valueConversionService == null
                ? ValueConversionService.SqlTypeFamily.VARCHAR
                : valueConversionService.familyFromSqlType(column.getSqlType());
        if (valueConversionService != null) {
            valueConversionService.bind(statement, parameterIndex, rawValue, family);
            return;
        }
        // Fallback for unit tests without conversion service
        String trimmed = rawValue == null ? "" : rawValue.trim();
        if (trimmed.isEmpty()) {
            statement.setNull(parameterIndex, Types.VARCHAR);
        } else {
            statement.setString(parameterIndex, trimmed);
        }
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

    /**
     * On H2 (local/test) create missing schemas so CREATE TABLE works without
     * a pre-provisioned library. On IBM i this is a no-op.
     */
    private void ensureSchemaExists(Connection connection, SqlDialect dialect, String library) throws SQLException {
        if (!dialect.supportsSchemaAutoCreate() || !StringUtils.hasText(library)) {
            return;
        }
        String schemaSql = dialect.createSchemaSql(library);
        if (!StringUtils.hasText(schemaSql)) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(schemaSql)) {
            statement.executeUpdate();
            log.debug("Ensured schema/library exists: {}", library);
        }
    }
}
