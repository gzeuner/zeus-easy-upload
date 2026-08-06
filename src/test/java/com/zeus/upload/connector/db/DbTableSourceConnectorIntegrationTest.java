package com.zeus.upload.connector.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeus.upload.flow.DbTableSourceConfiguration;
import com.zeus.upload.flow.DataRecord;
import com.zeus.upload.sql.SqlDialect;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DbTableSourceConnectorIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private SqlDialect sqlDialect;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldReadRowsAsNeutralRecords() {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS \"TESTLIB\"");
        jdbcTemplate.execute("DROP TABLE IF EXISTS \"TESTLIB\".\"H2_SOURCE_IT\"");
        jdbcTemplate.execute("CREATE TABLE \"TESTLIB\".\"H2_SOURCE_IT\" (\"ID\" INTEGER, \"NAME\" VARCHAR(64))");
        jdbcTemplate.update("INSERT INTO \"TESTLIB\".\"H2_SOURCE_IT\" VALUES (?, ?)", 1, "Alice");

        List<DataRecord> records = new DbTableSourceConnector(
                dataSource, sqlDialect,
                new DbTableSourceConfiguration("TESTLIB", "H2_SOURCE_IT", List.of()))
                .read().toList();

        assertThat(records).hasSize(1);
        assertThat(records.get(0).get("ID")).isEqualTo(1);
        assertThat(records.get(0).get("NAME")).isEqualTo("Alice");
    }
}
