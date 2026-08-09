package com.zeus.upload.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PostgresDialectTest {

    private final PostgresDialect dialect = new PostgresDialect();

    @Test
    void productAndPolicy() {
        assertThat(dialect.product()).isEqualTo(DatabaseProduct.POSTGRES);
        assertThat(dialect.identifierPolicy().maxLength()).isEqualTo(63);
        assertThat(dialect.supportsSchemaAutoCreate()).isTrue();
        assertThat(dialect.upsertStrategy()).isEqualTo(UpsertStrategy.POSTGRES_ON_CONFLICT);
    }

    @Test
    void upsertUsesOnConflict() {
        UpsertSql upsert = dialect.buildUpsertSql(
                "public",
                "orders",
                List.of("ID", "NAME"),
                List.of("NAME"),
                List.of("ID")
        );
        assertThat(upsert.strategy()).isEqualTo(UpsertStrategy.POSTGRES_ON_CONFLICT);
        assertThat(upsert.sql()).isEqualTo(
                "INSERT INTO \"PUBLIC\".\"ORDERS\" (\"ID\", \"NAME\") VALUES (?, ?) "
                        + "ON CONFLICT (\"ID\") DO UPDATE SET \"NAME\" = EXCLUDED.\"NAME\""
        );
    }
}
