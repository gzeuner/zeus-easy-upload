package com.zeus.upload.connector.db;

import com.zeus.upload.connector.SourceConnector;
import com.zeus.upload.flow.DataRecord;
import com.zeus.upload.flow.DbTableSourceConfiguration;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import javax.sql.DataSource;
import com.zeus.upload.sql.SqlDialect;

public class DbTableSourceConnector implements SourceConnector {

    private final DataSource dataSource;
    private final SqlDialect sqlDialect;
    private final DbTableSourceConfiguration configuration;

    public DbTableSourceConnector(DataSource dataSource, SqlDialect sqlDialect, DbTableSourceConfiguration configuration) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.sqlDialect = Objects.requireNonNull(sqlDialect);
        this.configuration = Objects.requireNonNull(configuration);
    }

    @Override
    public Stream<DataRecord> read() {
        List<DataRecord> records = new ArrayList<>();
        String projection = configuration.getColumns().isEmpty()
                ? "*"
                : configuration.getColumns().stream().map(sqlDialect::quoteIdentifier).reduce((a, b) -> a + ", " + b).orElse("*");
        String sql = "SELECT " + projection + " FROM "
                + sqlDialect.qualifyTable(configuration.getLibrary(), configuration.getTableName());
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            int columnCount = resultSet.getMetaData().getColumnCount();
            while (resultSet.next()) {
                DataRecord record = new DataRecord();
                for (int index = 1; index <= columnCount; index++) {
                    record.set(resultSet.getMetaData().getColumnLabel(index), resultSet.getObject(index));
                }
                records.add(record);
            }
            return records.stream();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read DB source: " + ex.getMessage(), ex);
        }
    }
}
