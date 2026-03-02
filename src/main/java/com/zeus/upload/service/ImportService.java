package com.zeus.upload.service;

import com.zeus.upload.config.AppProperties;
import com.zeus.upload.domain.ColumnProposal;
import com.zeus.upload.domain.ImportRequest;
import com.zeus.upload.domain.ImportResult;
import com.zeus.upload.domain.ParseError;
import com.zeus.upload.domain.ParsedCsv;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private final DataSource dataSource;
    private final DdlService ddlService;
    private final TypeInferenceService typeInferenceService;
    private final AppProperties appProperties;

    public ImportService(
            DataSource dataSource,
            DdlService ddlService,
            TypeInferenceService typeInferenceService,
            AppProperties appProperties
    ) {
        this.dataSource = dataSource;
        this.ddlService = ddlService;
        this.typeInferenceService = typeInferenceService;
        this.appProperties = appProperties;
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
}
