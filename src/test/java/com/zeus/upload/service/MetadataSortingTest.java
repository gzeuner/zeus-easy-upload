package com.zeus.upload.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeus.upload.domain.DbColumnMeta;
import java.util.List;
import org.junit.jupiter.api.Test;

class MetadataSortingTest {

    @Test
    void shouldSortColumnsByOrdinalPositionFirst() {
        DbColumnMeta c1 = new DbColumnMeta("C_LAST", "VARCHAR", 12, 100, 100, 0, true, null, 3);
        DbColumnMeta c2 = new DbColumnMeta("C_FIRST", "INTEGER", 4, 10, 10, 0, false, null, 1);
        DbColumnMeta c3 = new DbColumnMeta("C_SECOND", "DECIMAL", 3, 15, 15, 2, true, null, 2);

        List<DbColumnMeta> sorted = JdbcMetadataService.sortColumnsForTest(List.of(c1, c2, c3));

        assertThat(sorted).extracting(DbColumnMeta::getColumnName)
                .containsExactly("C_FIRST", "C_SECOND", "C_LAST");
    }

    @Test
    void shouldFallbackToColumnNameWhenOrdinalMissing() {
        DbColumnMeta z = new DbColumnMeta("Z_COL", "VARCHAR", 12, 20, 20, 0, true, null, null);
        DbColumnMeta a = new DbColumnMeta("A_COL", "VARCHAR", 12, 20, 20, 0, true, null, null);
        DbColumnMeta withOrdinal = new DbColumnMeta("MID_COL", "VARCHAR", 12, 20, 20, 0, true, null, 2);

        List<DbColumnMeta> sorted = JdbcMetadataService.sortColumnsForTest(List.of(z, a, withOrdinal));

        assertThat(sorted).extracting(DbColumnMeta::getColumnName)
                .containsExactly("MID_COL", "A_COL", "Z_COL");
    }
}
