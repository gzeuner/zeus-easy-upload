package com.zeus.upload.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeus.upload.domain.ColumnMapping;
import com.zeus.upload.domain.DbColumnMeta;
import com.zeus.upload.domain.ImportRequest;
import com.zeus.upload.domain.ImportResult;
import com.zeus.upload.domain.MappingValidationResult;
import com.zeus.upload.domain.ParsedCsv;
import com.zeus.upload.service.CsvParsingService;
import com.zeus.upload.service.ImportService;
import com.zeus.upload.service.MappingService;
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
class H2ImportIntegrationTest {

    private static final String LIBRARY = "TESTLIB";

    @Autowired
    private CsvParsingService csvParsingService;

    @Autowired
    private ImportService importService;

    @Autowired
    private MappingService mappingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldImportCsvByCreatingTableRoundTrip() throws IOException {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS \"TESTLIB\"");
        ParsedCsv parsedCsv = parseSampleCsv();

        ImportRequest request = new ImportRequest();
        request.setLibrary(LIBRARY);
        request.setTableName("H2_CREATE_IMPORT_IT");
        request.setColumns(new ArrayList<>(parsedCsv.getProposals()));

        ImportResult result = importService.importCsv(request, parsedCsv);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getInsertedRows()).isEqualTo(parsedCsv.getRows().size());

        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"TESTLIB\".\"H2_CREATE_IMPORT_IT\"",
                Integer.class
        );
        String name = jdbcTemplate.queryForObject(
                "SELECT \"NAME\" FROM \"TESTLIB\".\"H2_CREATE_IMPORT_IT\" WHERE \"ID\" = 1",
                String.class
        );
        assertThat(rowCount).isEqualTo(3);
        assertThat(name).isEqualTo("Alice");
    }

    @Test
    void shouldImportIntoExistingTableUsingMappingsRoundTrip() throws IOException {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS \"TESTLIB\"");
        jdbcTemplate.execute("DROP TABLE IF EXISTS \"TESTLIB\".\"H2_EXISTING_IMPORT_IT\"");
        jdbcTemplate.execute("""
                CREATE TABLE "TESTLIB"."H2_EXISTING_IMPORT_IT" (
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

        List<ColumnMapping> mappings = mappingsForHeaders(parsedCsv.getOriginalHeaders());
        MappingValidationResult validationResult = mappingService.validate(parsedCsv, dbColumns, mappings);
        assertThat(validationResult.isValid()).isTrue();

        ImportResult result = importService.importIntoExistingTable(
                LIBRARY,
                "H2_EXISTING_IMPORT_IT",
                parsedCsv,
                dbColumns,
                mappings
        );

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getInsertedRows()).isEqualTo(3);

        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"TESTLIB\".\"H2_EXISTING_IMPORT_IT\"",
                Integer.class
        );
        String source = jdbcTemplate.queryForObject(
                "SELECT \"SOURCE\" FROM \"TESTLIB\".\"H2_EXISTING_IMPORT_IT\" WHERE \"ID\" = 1",
                String.class
        );
        assertThat(rowCount).isEqualTo(3);
        assertThat(source).isEqualTo("CSV");
    }

    @Test
    void shouldFailValidationWhenRequiredColumnWithoutDefaultIsNotMapped() {
        ParsedCsv parsedCsv = new ParsedCsv();
        parsedCsv.getOriginalHeaders().addAll(List.of("id", "name"));

        List<DbColumnMeta> dbColumns = List.of(
                new DbColumnMeta("ID", "INTEGER", java.sql.Types.INTEGER, 10, 10, 0, false, null, 1),
                new DbColumnMeta("NAME", "VARCHAR", java.sql.Types.VARCHAR, 100, 100, 0, false, null, 2),
                new DbColumnMeta("REQUIRED_NO_DEFAULT", "VARCHAR", java.sql.Types.VARCHAR, 100, 100, 0, false, null, 3)
        );
        List<ColumnMapping> mappings = List.of(
                mapping(0, "id", "ID", false),
                mapping(1, "name", "NAME", false)
        );

        MappingValidationResult validationResult = mappingService.validate(parsedCsv, dbColumns, mappings);

        assertThat(validationResult.isValid()).isFalse();
        assertThat(validationResult.getErrors()).anyMatch(error -> error.contains("REQUIRED_NO_DEFAULT"));
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
            mappings.add(mapping(i, header, target, false));
        }
        return mappings;
    }

    private ColumnMapping mapping(int csvIndex, String csvColumn, String targetColumn, boolean ignored) {
        ColumnMapping mapping = new ColumnMapping();
        mapping.setCsvIndex(csvIndex);
        mapping.setCsvColumn(csvColumn);
        mapping.setTargetColumn(targetColumn);
        mapping.setIgnored(ignored);
        return mapping;
    }
}
