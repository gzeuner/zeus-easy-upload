package com.zeus.upload.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class MergeSqlBuilderTest {

    @Test
    void shouldBuildDeterministicDb2MergeSql() {
        MergeSqlBuilder builder = new MergeSqlBuilder(new Db2iDialect());

        String sql = builder.buildDb2MergeSql(
                "bib",
                "orders",
                List.of("ID", "NAME", "AMOUNT"),
                List.of("NAME", "AMOUNT"),
                List.of("ID")
        );

        assertThat(sql).isEqualTo(
                "MERGE INTO \"BIB\".\"ORDERS\" AS T "
                        + "USING (VALUES (?, ?, ?)) AS S(\"ID\", \"NAME\", \"AMOUNT\") "
                        + "ON (T.\"ID\" = S.\"ID\") "
                        + "WHEN MATCHED THEN UPDATE SET T.\"NAME\" = S.\"NAME\", T.\"AMOUNT\" = S.\"AMOUNT\" "
                        + "WHEN NOT MATCHED THEN INSERT (\"ID\", \"NAME\", \"AMOUNT\") VALUES (S.\"ID\", S.\"NAME\", S.\"AMOUNT\")"
        );
    }

    @Test
    void shouldUseAllKeyColumnsInOnClause() {
        MergeSqlBuilder builder = new MergeSqlBuilder(new Db2iDialect());

        String sql = builder.buildDb2MergeSql(
                "bib",
                "orders",
                List.of("ID", "TENANT", "NAME"),
                List.of("NAME"),
                List.of("ID", "TENANT")
        );

        assertThat(sql).contains("ON (T.\"ID\" = S.\"ID\" AND T.\"TENANT\" = S.\"TENANT\")");
        assertThat(sql).contains("UPDATE SET T.\"NAME\" = S.\"NAME\"");
    }

    @Test
    void shouldRejectMergeWithoutUpdateColumns() {
        MergeSqlBuilder builder = new MergeSqlBuilder(new Db2iDialect());

        assertThatThrownBy(() -> builder.buildDb2MergeSql(
                "bib",
                "orders",
                List.of("ID"),
                List.of(),
                List.of("ID")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-key update column");
    }
}
