package com.zeus.upload.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.zeus.upload.config.AppProperties;
import com.zeus.upload.domain.ColumnMapping;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class ExistingTableImportSqlTest {

    @Test
    void shouldBuildInsertSqlWithMappedColumnOrder() {
        AppProperties properties = new AppProperties();
        DataSource dataSource = mock(DataSource.class);
        ImportService importService = new ImportService(dataSource, null, null, properties);
        List<ColumnMapping> mappings = List.of(
                mapping(2, "amount", "AMOUNT"),
                mapping(0, "id", "ID"),
                mapping(1, "created_at", "CREATED_AT")
        );

        String sql = importService.buildInsertSql("bib", "orders", mappings);

        assertThat(sql).isEqualTo("INSERT INTO BIB.ORDERS (AMOUNT, ID, CREATED_AT) VALUES (?, ?, ?)");
    }

    private ColumnMapping mapping(int csvIndex, String csvColumn, String targetColumn) {
        ColumnMapping mapping = new ColumnMapping();
        mapping.setCsvIndex(csvIndex);
        mapping.setCsvColumn(csvColumn);
        mapping.setTargetColumn(targetColumn);
        mapping.setIgnored(false);
        return mapping;
    }
}
