package com.zeus.upload.service;

import com.zeus.upload.domain.DbColumnMeta;
import com.zeus.upload.domain.DbTableRef;
import com.zeus.upload.util.MetadataNormalizationUtil;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JdbcMetadataService implements MetadataService {

    private static final Logger log = LoggerFactory.getLogger(JdbcMetadataService.class);

    private static final Comparator<DbColumnMeta> COLUMN_COMPARATOR = Comparator
            .comparing((DbColumnMeta c) -> c.getOrdinalPosition() == null ? Integer.MAX_VALUE : c.getOrdinalPosition())
            .thenComparing(c -> c.getColumnName() == null ? "" : c.getColumnName());

    private final DataSource dataSource;

    public JdbcMetadataService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<DbTableRef> listTables(String library) {
        String normalizedLibrary = MetadataNormalizationUtil.normalizeLibrary(library);
        if (normalizedLibrary.isEmpty()) {
            return List.of();
        }

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            List<DbTableRef> result = fetchTables(metadata, normalizedLibrary, false);

            if (result.isEmpty()) {
                result = fetchTables(metadata, normalizedLibrary, true);
            }

            if (result.isEmpty()) {
                log.warn("No metadata tables found for library {}", normalizedLibrary);
            }
            return result;
        } catch (SQLException ex) {
            log.error("Failed to list metadata tables for library {}", normalizedLibrary, ex);
            throw new RuntimeException("Failed to list tables for library " + normalizedLibrary, ex);
        }
    }

    @Override
    public List<DbColumnMeta> listColumns(String library, String tableName) {
        String normalizedLibrary = MetadataNormalizationUtil.normalizeLibrary(library);
        String normalizedTable = MetadataNormalizationUtil.normalizeTable(tableName);
        if (normalizedLibrary.isEmpty() || normalizedTable.isEmpty()) {
            return List.of();
        }

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            List<DbColumnMeta> columns = new ArrayList<>();
            try (ResultSet resultSet = metadata.getColumns(null, normalizedLibrary, normalizedTable, "%")) {
                while (resultSet.next()) {
                    String columnName = MetadataNormalizationUtil.normalizeTable(resultSet.getString("COLUMN_NAME"));
                    String typeName = resultSet.getString("TYPE_NAME");
                    Integer jdbcType = readInteger(resultSet, "DATA_TYPE");
                    Integer length = readInteger(resultSet, "COLUMN_SIZE");
                    Integer scale = readInteger(resultSet, "DECIMAL_DIGITS");
                    Integer nullableValue = readInteger(resultSet, "NULLABLE");
                    String defaultValue = resultSet.getString("COLUMN_DEF");
                    Integer ordinalPosition = readInteger(resultSet, "ORDINAL_POSITION");
                    boolean nullable = nullableValue == null || nullableValue != DatabaseMetaData.columnNoNulls;

                    columns.add(new DbColumnMeta(
                            columnName,
                            typeName,
                            jdbcType,
                            length,
                            length,
                            scale,
                            nullable,
                            defaultValue,
                            ordinalPosition
                    ));
                }
            }

            if (columns.isEmpty()) {
                log.warn("No metadata columns found for {}.{}", normalizedLibrary, normalizedTable);
                return List.of();
            }

            columns.sort(COLUMN_COMPARATOR);
            return columns;
        } catch (SQLException ex) {
            log.error("Failed to list metadata columns for {}.{}", normalizedLibrary, normalizedTable, ex);
            throw new RuntimeException(
                    "Failed to list columns for " + normalizedLibrary + "." + normalizedTable,
                    ex
            );
        }
    }

    static List<DbColumnMeta> sortColumnsForTest(List<DbColumnMeta> columns) {
        List<DbColumnMeta> sorted = new ArrayList<>(columns);
        sorted.sort(COLUMN_COMPARATOR);
        return sorted;
    }

    private List<DbTableRef> fetchTables(DatabaseMetaData metadata, String library, boolean fallback) throws SQLException {
        String schemaPattern = fallback ? null : library;
        Map<String, DbTableRef> unique = new LinkedHashMap<>();
        try (ResultSet resultSet = metadata.getTables(null, schemaPattern, "%", new String[]{"TABLE"})) {
            while (resultSet.next()) {
                String tableSchema = MetadataNormalizationUtil.normalizeLibrary(resultSet.getString("TABLE_SCHEM"));
                if (fallback && !library.equals(tableSchema)) {
                    continue;
                }

                String tableName = MetadataNormalizationUtil.normalizeTable(resultSet.getString("TABLE_NAME"));
                if (tableName.isEmpty()) {
                    continue;
                }
                unique.put(tableName, new DbTableRef(library, tableName));
            }
        }

        List<DbTableRef> result = new ArrayList<>(unique.values());
        result.sort(Comparator.comparing(DbTableRef::getTableName));
        return result;
    }

    private Integer readInteger(ResultSet resultSet, String columnLabel) throws SQLException {
        Object value = resultSet.getObject(columnLabel);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }
}
