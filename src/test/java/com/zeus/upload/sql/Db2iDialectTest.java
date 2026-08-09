package com.zeus.upload.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class Db2iDialectTest {

    private final Db2iDialect dialect = new Db2iDialect();

    @Test
    void productAndPolicy() {
        assertThat(dialect.product()).isEqualTo(DatabaseProduct.DB2_I);
        assertThat(dialect.identifierPolicy().maxLength()).isEqualTo(10);
        assertThat(dialect.supportsSchemaAutoCreate()).isFalse();
        assertThat(dialect.createSchemaSql("BIB")).isNull();
        assertThat(dialect.upsertStrategy()).isEqualTo(UpsertStrategy.DB2_MERGE_VALUES);
    }

    @Test
    void qualifiesLibraryAndTable() {
        assertThat(dialect.qualifyTable("bib", "person")).isEqualTo("\"BIB\".\"PERSON\"");
    }

    @Test
    void columnTypesMatchHistoricDb2Limits() {
        assertThat(dialect.columnTypeDefinition("DECIMAL", null, 40, 2)).isEqualTo("DECIMAL(31,2)");
        assertThat(dialect.columnTypeDefinition("VARCHAR", 9000, null, null)).isEqualTo("VARCHAR(4000)");
        assertThat(dialect.columnTypeDefinition("INTEGER", null, null, null)).isEqualTo("INTEGER");
    }

    @Test
    void upsertBuildsStableDb2MergeSql() {
        UpsertSql upsert = dialect.buildUpsertSql(
                "bib",
                "orders",
                List.of("ID", "NAME", "AMOUNT"),
                List.of("NAME", "AMOUNT"),
                List.of("ID")
        );

        assertThat(upsert.strategy()).isEqualTo(UpsertStrategy.DB2_MERGE_VALUES);
        assertThat(upsert.sql()).isEqualTo(
                "MERGE INTO \"BIB\".\"ORDERS\" AS T "
                        + "USING (VALUES (?, ?, ?)) AS S(\"ID\", \"NAME\", \"AMOUNT\") "
                        + "ON (T.\"ID\" = S.\"ID\") "
                        + "WHEN MATCHED THEN UPDATE SET T.\"NAME\" = S.\"NAME\", T.\"AMOUNT\" = S.\"AMOUNT\" "
                        + "WHEN NOT MATCHED THEN INSERT (\"ID\", \"NAME\", \"AMOUNT\") VALUES (S.\"ID\", S.\"NAME\", S.\"AMOUNT\")"
        );
    }
}
