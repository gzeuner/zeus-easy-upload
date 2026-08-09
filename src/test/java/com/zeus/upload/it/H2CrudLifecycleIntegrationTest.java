package com.zeus.upload.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeus.upload.connector.db.DbTableSourceConnector;
import com.zeus.upload.domain.ColumnMapping;
import com.zeus.upload.domain.ColumnProposal;
import com.zeus.upload.domain.DbColumnMeta;
import com.zeus.upload.domain.DbTableRef;
import com.zeus.upload.domain.ImportRequest;
import com.zeus.upload.domain.ImportResult;
import com.zeus.upload.domain.ParsedCsv;
import com.zeus.upload.flow.DataRecord;
import com.zeus.upload.flow.DbTableSourceConfiguration;
import com.zeus.upload.service.ImportService;
import com.zeus.upload.service.MetadataService;
import com.zeus.upload.sql.SqlDialect;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * End-to-end proof that CREATE TABLE, INSERT, SELECT, UPDATE and DELETE work
 * against the local H2 database without IBM i.
 */
@SpringBootTest
@ActiveProfiles("test")
class H2CrudLifecycleIntegrationTest {

    private static final String LIBRARY = "TESTLIB";
    private static final String TABLE = "H2_CRUD_LIFECYCLE";

    @Autowired
    private ImportService importService;

    @Autowired
    private MetadataService metadataService;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private SqlDialect sqlDialect;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTable() {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS \"TESTLIB\"");
        jdbcTemplate.execute("DROP TABLE IF EXISTS \"TESTLIB\".\"H2_CRUD_LIFECYCLE\"");
    }

    @Test
    void createInsertSelectUpdateDeleteRoundTrip() {
        // --- CREATE TABLE + INSERT ---
        ImportRequest createRequest = new ImportRequest();
        createRequest.setLibrary(LIBRARY);
        createRequest.setTableName(TABLE);
        createRequest.setDropAndRecreate(true);
        createRequest.setColumns(List.of(
                proposal("ID", "INTEGER", false),
                proposal("NAME", "VARCHAR", true),
                proposal("AMOUNT", "DECIMAL", true)
        ));
        createRequest.getColumns().get(2).setPrecision(10);
        createRequest.getColumns().get(2).setScale(2);
        createRequest.getColumns().get(1).setLength(64);

        ParsedCsv insertCsv = rowsCsv(
                List.of("id", "name", "amount"),
                List.of(
                        List.of("1", "Alice", "10.50"),
                        List.of("2", "Bob", "20.00"),
                        List.of("3", "Charlie", "30.25")
                )
        );

        ImportResult createResult = importService.importCsv(createRequest, insertCsv);
        assertThat(createResult.isSuccess())
                .as("CREATE+INSERT should succeed: %s", createResult.getMessage())
                .isTrue();
        assertThat(createResult.getInsertedRows()).isEqualTo(3);
        assertThat(createResult.getCreateTableSql()).containsIgnoringCase("CREATE TABLE");

        Integer countAfterInsert = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"TESTLIB\".\"H2_CRUD_LIFECYCLE\"", Integer.class);
        assertThat(countAfterInsert).isEqualTo(3);

        // --- SELECT (source connector) ---
        List<DataRecord> selected = new DbTableSourceConnector(
                dataSource,
                sqlDialect,
                new DbTableSourceConfiguration(LIBRARY, TABLE, List.of("ID", "NAME", "AMOUNT"))
        ).read().toList();

        assertThat(selected).hasSize(3);
        assertThat(selected.stream().map(r -> String.valueOf(r.get("NAME"))).toList())
                .containsExactlyInAnyOrder("Alice", "Bob", "Charlie");

        // --- Metadata SELECT (list tables / columns) ---
        List<DbTableRef> tables = metadataService.listTables(LIBRARY);
        assertThat(tables.stream().map(DbTableRef::getTableName).toList())
                .contains(TABLE);

        List<DbColumnMeta> columns = metadataService.listColumns(LIBRARY, TABLE);
        assertThat(columns.stream().map(DbColumnMeta::getColumnName).toList())
                .contains("ID", "NAME", "AMOUNT");

        // --- UPDATE ---
        List<DbColumnMeta> dbColumns = columns;
        ParsedCsv updateCsv = rowsCsv(
                List.of("id", "name"),
                List.of(List.of("1", "Alicia"))
        );
        List<ColumnMapping> updateMappings = List.of(
                mapping(0, "id", "ID"),
                mapping(1, "name", "NAME")
        );
        ImportResult updateResult = importService.updateIntoExistingTable(
                LIBRARY, TABLE, updateCsv, dbColumns, updateMappings, List.of("ID"), false);
        assertThat(updateResult.isSuccess())
                .as("UPDATE should succeed: %s", updateResult.getMessage())
                .isTrue();

        String updatedName = jdbcTemplate.queryForObject(
                "SELECT \"NAME\" FROM \"TESTLIB\".\"H2_CRUD_LIFECYCLE\" WHERE \"ID\" = 1",
                String.class);
        assertThat(updatedName).isEqualTo("Alicia");

        // --- DELETE ---
        ParsedCsv deleteCsv = rowsCsv(List.of("id"), List.of(List.of("2")));
        List<ColumnMapping> deleteMappings = List.of(mapping(0, "id", "ID"));
        ImportResult deleteResult = importService.deleteFromExistingTable(
                LIBRARY, TABLE, deleteCsv, dbColumns, deleteMappings, List.of("ID"), false);
        assertThat(deleteResult.isSuccess())
                .as("DELETE should succeed: %s", deleteResult.getMessage())
                .isTrue();

        Integer countAfterDelete = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"TESTLIB\".\"H2_CRUD_LIFECYCLE\"", Integer.class);
        assertThat(countAfterDelete).isEqualTo(2);

        Integer remainingBob = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"TESTLIB\".\"H2_CRUD_LIFECYCLE\" WHERE \"ID\" = 2",
                Integer.class);
        assertThat(remainingBob).isZero();

        // --- INSERT into existing table ---
        ParsedCsv extraInsert = rowsCsv(
                List.of("id", "name", "amount"),
                List.of(List.of("4", "Dana", "40.00"))
        );
        List<ColumnMapping> insertMappings = List.of(
                mapping(0, "id", "ID"),
                mapping(1, "name", "NAME"),
                mapping(2, "amount", "AMOUNT")
        );
        ImportResult insertExisting = importService.importIntoExistingTable(
                LIBRARY, TABLE, extraInsert, dbColumns, insertMappings, false);
        assertThat(insertExisting.isSuccess())
                .as("INSERT existing should succeed: %s", insertExisting.getMessage())
                .isTrue();
        assertThat(insertExisting.getInsertedRows()).isEqualTo(1);

        Integer finalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"TESTLIB\".\"H2_CRUD_LIFECYCLE\"", Integer.class);
        assertThat(finalCount).isEqualTo(3);
    }

    @Test
    void createFailsClearlyWhenTableExistsWithoutDropAndRecreate() {
        ImportRequest first = new ImportRequest();
        first.setLibrary(LIBRARY);
        first.setTableName("H2_EXISTS_GUARD");
        first.setDropAndRecreate(true);
        first.setColumns(List.of(proposal("ID", "INTEGER", false), proposal("NAME", "VARCHAR", true)));
        first.getColumns().get(1).setLength(32);
        ParsedCsv csv = rowsCsv(List.of("id", "name"), List.of(List.of("1", "A")));
        assertThat(importService.importCsv(first, csv).isSuccess()).isTrue();

        ImportRequest second = new ImportRequest();
        second.setLibrary(LIBRARY);
        second.setTableName("H2_EXISTS_GUARD");
        second.setDropAndRecreate(false);
        second.setColumns(List.of(proposal("ID", "INTEGER", false), proposal("NAME", "VARCHAR", true)));
        second.getColumns().get(1).setLength(32);

        ImportResult result = importService.importCsv(second, csv);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).containsIgnoringCase("already exists");
        assertThat(result.getMessage()).containsIgnoringCase("Drop table");
    }

    @Test
    void dropAndRecreateAllowsCreateOnExistingTable() {
        ImportRequest request = new ImportRequest();
        request.setLibrary(LIBRARY);
        request.setTableName("H2_RECREATE_OK");
        request.setDropAndRecreate(true);
        request.setColumns(List.of(proposal("ID", "INTEGER", false), proposal("NAME", "VARCHAR", true)));
        request.getColumns().get(1).setLength(32);
        ParsedCsv csv = rowsCsv(List.of("id", "name"), List.of(List.of("1", "A"), List.of("2", "B")));

        assertThat(importService.importCsv(request, csv).isSuccess()).isTrue();
        ImportResult again = importService.importCsv(request, csv);
        assertThat(again.isSuccess()).as(again.getMessage()).isTrue();
        assertThat(again.getInsertedRows()).isEqualTo(2);
    }

    @Test
    void createTableAutoCreatesMissingSchemaOnH2() {
        String library = "AUTO_SCHEMA_LIB";
        String table = "H2_AUTO_SCHEMA";
        // Drop only if the schema already exists (H2 rejects DROP TABLE on missing schema).
        try {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS \"AUTO_SCHEMA_LIB\" CASCADE");
        } catch (Exception ignored) {
            // ignore if dialect/version does not support this form
        }

        ImportRequest request = new ImportRequest();
        request.setLibrary(library);
        request.setTableName(table);
        request.setColumns(List.of(proposal("ID", "INTEGER", false), proposal("LABEL", "VARCHAR", true)));
        request.getColumns().get(1).setLength(32);

        ParsedCsv csv = rowsCsv(List.of("id", "label"), List.of(List.of("1", "ok")));
        ImportResult result = importService.importCsv(request, csv);

        assertThat(result.isSuccess())
                .as("Schema auto-create + CREATE TABLE should work: %s", result.getMessage())
                .isTrue();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"AUTO_SCHEMA_LIB\".\"H2_AUTO_SCHEMA\"", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    private static ColumnProposal proposal(String name, String sqlType, boolean nullable) {
        ColumnProposal proposal = new ColumnProposal();
        proposal.setIndex(0);
        proposal.setOriginalName(name);
        proposal.setSanitizedName(name);
        proposal.setFinalName(name);
        proposal.setDetectedType(sqlType);
        proposal.setSqlType(sqlType);
        proposal.setNullable(nullable);
        return proposal;
    }

    private static ParsedCsv rowsCsv(List<String> headers, List<List<String>> rows) {
        ParsedCsv csv = new ParsedCsv();
        csv.getOriginalHeaders().addAll(headers);
        csv.getRows().addAll(rows);
        return csv;
    }

    private static ColumnMapping mapping(int csvIndex, String csvColumn, String targetColumn) {
        ColumnMapping mapping = new ColumnMapping();
        mapping.setCsvIndex(csvIndex);
        mapping.setCsvColumn(csvColumn);
        mapping.setTargetColumn(targetColumn);
        mapping.setIgnored(false);
        return mapping;
    }
}
