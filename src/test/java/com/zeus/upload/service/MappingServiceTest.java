package com.zeus.upload.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeus.upload.domain.ColumnMapping;
import com.zeus.upload.domain.DbColumnMeta;
import com.zeus.upload.domain.MappingValidationResult;
import com.zeus.upload.domain.ParsedCsv;
import java.util.List;
import org.junit.jupiter.api.Test;

class MappingServiceTest {

    private final MappingService service = new MappingService();

    @Test
    void shouldMatchNormalizedExactName() {
        ParsedCsv csv = parsedCsvWithHeaders("first name");
        List<DbColumnMeta> dbColumns = List.of(dbColumn("FIRST_NAME", false, null));

        List<ColumnMapping> mappings = service.autoMap(csv, dbColumns);

        assertThat(mappings).hasSize(1);
        assertThat(mappings.get(0).getTargetColumn()).isEqualTo("FIRST_NAME");
    }

    @Test
    void shouldMatchHyphenName() {
        ParsedCsv csv = parsedCsvWithHeaders("first-name");
        List<DbColumnMeta> dbColumns = List.of(dbColumn("FIRST_NAME", false, null));

        List<ColumnMapping> mappings = service.autoMap(csv, dbColumns);

        assertThat(mappings).hasSize(1);
        assertThat(mappings.get(0).getTargetColumn()).isEqualTo("FIRST_NAME");
    }

    @Test
    void shouldMatchIgnoringUnderscores() {
        ParsedCsv csv = parsedCsvWithHeaders("firstname");
        List<DbColumnMeta> dbColumns = List.of(dbColumn("FIRST_NAME", false, null));

        List<ColumnMapping> mappings = service.autoMap(csv, dbColumns);

        assertThat(mappings).hasSize(1);
        assertThat(mappings.get(0).getTargetColumn()).isEqualTo("FIRST_NAME");
    }

    @Test
    void shouldDetectDuplicateTargetMapping() {
        ParsedCsv csv = parsedCsvWithHeaders("a", "b");
        List<DbColumnMeta> dbColumns = List.of(dbColumn("FIRST_NAME", true, null));
        List<ColumnMapping> mappings = List.of(
                mapping(0, "a", "FIRST_NAME", false),
                mapping(1, "b", "FIRST_NAME", false)
        );

        MappingValidationResult result = service.validate(csv, dbColumns, mappings);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(error -> error.contains("mapped more than once"));
    }

    @Test
    void shouldEnforceNotNullWithoutDefault() {
        ParsedCsv csv = parsedCsvWithHeaders("first");
        List<DbColumnMeta> dbColumns = List.of(dbColumn("REQUIRED_COL", false, null));
        List<ColumnMapping> mappings = List.of(mapping(0, "first", "", false));

        MappingValidationResult result = service.validate(csv, dbColumns, mappings);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(error -> error.contains("REQUIRED_COL"));
    }

    @Test
    void shouldAllowNotNullWithDefault() {
        ParsedCsv csv = parsedCsvWithHeaders("first");
        List<DbColumnMeta> dbColumns = List.of(dbColumn("REQUIRED_COL", false, "CURRENT_TIMESTAMP"));
        List<ColumnMapping> mappings = List.of(mapping(0, "first", "", false));

        MappingValidationResult result = service.validate(csv, dbColumns, mappings);

        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void shouldWarnForUnmappedCsvColumn() {
        ParsedCsv csv = parsedCsvWithHeaders("first");
        List<DbColumnMeta> dbColumns = List.of(dbColumn("OTHER_COL", true, null));
        List<ColumnMapping> mappings = List.of(mapping(0, "first", "", false));

        MappingValidationResult result = service.validate(csv, dbColumns, mappings);

        assertThat(result.getWarnings()).anyMatch(warning -> warning.contains("CSV column 'first' is unmapped"));
    }

    private ParsedCsv parsedCsvWithHeaders(String... headers) {
        ParsedCsv csv = new ParsedCsv();
        csv.getOriginalHeaders().addAll(List.of(headers));
        return csv;
    }

    private DbColumnMeta dbColumn(String name, boolean nullable, String defaultValue) {
        return new DbColumnMeta(name, "VARCHAR", java.sql.Types.VARCHAR, 255, 255, 0, nullable, defaultValue, 1);
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
