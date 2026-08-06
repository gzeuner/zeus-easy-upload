package com.zeus.upload.connector.db;

import com.zeus.upload.connector.SourceConnector;
import com.zeus.upload.flow.DataRecord;
import com.zeus.upload.flow.DbTableSourceConfiguration;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
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
        String projection = configuration.getColumns().isEmpty()
                ? "*"
                : configuration.getColumns().stream().map(sqlDialect::quoteIdentifier).reduce((a, b) -> a + ", " + b).orElse("*");
        String sql = "SELECT " + projection + " FROM "
                + sqlDialect.qualifyTable(configuration.getLibrary(), configuration.getTableName());
        Connection connection = null;
        Statement statement = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.createStatement();
            statement.setFetchSize(500);
            ResultSet resultSet = statement.executeQuery(sql);
            return streamRows(connection, statement, resultSet);
        } catch (SQLException ex) {
            try { if (statement != null) statement.close(); } catch (SQLException ignored) { }
            try { if (connection != null) connection.close(); } catch (SQLException ignored) { }
            throw new IllegalStateException("Could not read DB source: " + ex.getMessage(), ex);
        }
    }

    private Stream<DataRecord> streamRows(Connection connection, Statement statement, ResultSet resultSet)
            throws SQLException {
        int columnCount = resultSet.getMetaData().getColumnCount();
        List<String> columnLabels = new java.util.ArrayList<>(columnCount);
        for (int index = 1; index <= columnCount; index++) {
            columnLabels.add(resultSet.getMetaData().getColumnLabel(index));
        }

        Iterator<DataRecord> iterator = new Iterator<>() {
            private boolean advanced;
            private boolean hasNext;
            private boolean closed;

            @Override
            public boolean hasNext() {
                if (closed) return false;
                if (!advanced) {
                    try {
                        hasNext = resultSet.next();
                        advanced = true;
                        if (!hasNext) closeResources();
                    } catch (SQLException ex) {
                        closeResources();
                        throw new IllegalStateException("Could not read DB source row: " + ex.getMessage(), ex);
                    }
                }
                return hasNext;
            }

            @Override
            public DataRecord next() {
                if (!hasNext()) throw new java.util.NoSuchElementException();
                advanced = false;
                DataRecord record = new DataRecord();
                try {
                    for (int index = 1; index <= columnLabels.size(); index++) {
                        record.set(columnLabels.get(index - 1), resultSet.getObject(index));
                    }
                    return record;
                } catch (SQLException ex) {
                    closeResources();
                    throw new IllegalStateException("Could not read DB source row: " + ex.getMessage(), ex);
                }
            }

            private void closeResources() {
                if (closed) return;
                closed = true;
                try { resultSet.close(); } catch (SQLException ignored) { }
                try { statement.close(); } catch (SQLException ignored) { }
                try { connection.close(); } catch (SQLException ignored) { }
            }
        };

        return StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(iterator, java.util.Spliterator.ORDERED), false)
                .onClose(() -> {
                    try { resultSet.close(); } catch (SQLException ignored) { }
                    try { statement.close(); } catch (SQLException ignored) { }
                    try { connection.close(); } catch (SQLException ignored) { }
                });
    }
}
