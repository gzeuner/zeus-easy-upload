package com.zeus.upload.connector.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeus.upload.connector.file.CsvSourceConnector;
import com.zeus.upload.domain.ColumnMapping;
import com.zeus.upload.domain.DbColumnMeta;
import com.zeus.upload.domain.ImportResult;
import com.zeus.upload.domain.ParsedCsv;
import com.zeus.upload.flow.DataFlow;
import com.zeus.upload.flow.DbTableTargetConfiguration;
import com.zeus.upload.flow.DbTableWriteMode;
import com.zeus.upload.service.CsvParsingService;
import com.zeus.upload.service.ImportService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DbTableTargetConnectorIntegrationTest {

    private static final String LIBRARY = "TESTLIB";

    @Autowired
    private CsvParsingService csvParsingService;

    @Autowired
    private ImportService importService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldWriteExistingTableFlowThroughConnectorAdapter() throws IOException {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS \"TESTLIB\"");
        jdbcTemplate.execute("DROP TABLE IF EXISTS \"TESTLIB\".\"H2_CONNECTOR_IMPORT_IT\"");
        jdbcTemplate.execute("""
                CREATE TABLE "TESTLIB"."H2_CONNECTOR_IMPORT_IT" (
                  "ID" INTEGER NOT NULL,
                  "NAME" VARCHAR(128) NOT NULL,
                  "SOURCE" VARCHAR(20) NOT NULL DEFAULT 'CSV'
                )
                """);

        ParsedCsv parsedCsv = parseSampleCsv();
        List<DbColumnMeta> dbColumns = List.of(
                new DbColumnMeta("ID", "INTEGER", java.sql.Types.INTEGER, 10, 10, 0, false, null, 1),
                new DbColumnMeta("NAME", "VARCHAR", java.sql.Types.VARCHAR, 128, 128, 0, false, null, 2),
                new DbColumnMeta("SOURCE", "VARCHAR", java.sql.Types.VARCHAR, 20, 20, 0, false, "'CSV'", 3)
        );
        DbTableTargetConfiguration configuration = new DbTableTargetConfiguration(
                LIBRARY,
                "H2_CONNECTOR_IMPORT_IT",
                DbTableWriteMode.INSERT_EXISTING,
                false,
                List.of(),
                mappingsForHeaders(parsedCsv.getOriginalHeaders()),
                List.of(),
                dbColumns
        );

        DbTableTargetConnector target = new DbTableTargetConnector(importService, configuration);
        new DataFlow(new CsvSourceConnector(parsedCsv), target).execute();
        ImportResult result = target.getResult();

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getInsertedRows()).isEqualTo(3);

        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"TESTLIB\".\"H2_CONNECTOR_IMPORT_IT\"",
                Integer.class
        );
        String source = jdbcTemplate.queryForObject(
                "SELECT \"SOURCE\" FROM \"TESTLIB\".\"H2_CONNECTOR_IMPORT_IT\" WHERE \"ID\" = 1",
                String.class
        );
        assertThat(rowCount).isEqualTo(3);
        assertThat(source).isEqualTo("CSV");
    }

    private ParsedCsv parseSampleCsv() throws IOException {
        ClassPathResource resource = new ClassPathResource("examples/sample.csv");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.csv",
                "text/csv",
                resource.getInputStream()
        );
        return csvParsingService.parse(file);
    }

    private List<ColumnMapping> mappingsForHeaders(List<String> headers) {
        List<ColumnMapping> mappings = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            String target = null;
            if ("id".equalsIgnoreCase(header)) {
                target = "ID";
            }
            if ("name".equalsIgnoreCase(header)) {
                target = "NAME";
            }
            ColumnMapping mapping = new ColumnMapping();
            mapping.setCsvIndex(i);
            mapping.setCsvColumn(header);
            mapping.setTargetColumn(target);
            mapping.setIgnored(false);
            mappings.add(mapping);
        }
        return mappings;
    }
}
