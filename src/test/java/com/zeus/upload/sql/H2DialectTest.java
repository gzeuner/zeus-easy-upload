package com.zeus.upload.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class H2DialectTest {

    private final H2Dialect dialect = new H2Dialect();

    @Test
    void quotesAndQualifiesIdentifiers() {
        assertThat(dialect.product()).isEqualTo(DatabaseProduct.H2);
        assertThat(dialect.quoteIdentifier("testlib")).isEqualTo("\"TESTLIB\"");
        assertThat(dialect.qualifyTable("testlib", "my_table")).isEqualTo("\"TESTLIB\".\"MY_TABLE\"");
    }

    @Test
    void supportsSchemaAutoCreate() {
        assertThat(dialect.supportsSchemaAutoCreate()).isTrue();
        assertThat(dialect.createSchemaSql("testlib")).isEqualTo("CREATE SCHEMA IF NOT EXISTS \"TESTLIB\"");
    }

    @Test
    void upsertIsFailClosed() {
        assertThat(dialect.upsertStrategy()).isEqualTo(UpsertStrategy.UNSUPPORTED);
        assertThatThrownBy(() -> dialect.buildUpsertSql(
                "TESTLIB", "T", List.of("ID", "NAME"), List.of("NAME"), List.of("ID")))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("H2");
    }

    @Test
    void portableDmlBuilders() {
        assertThat(dialect.buildInsertSql("lib", "t", List.of("A", "B")))
                .isEqualTo("INSERT INTO \"LIB\".\"T\" (\"A\", \"B\") VALUES (?, ?)");
        assertThat(dialect.buildUpdateSql("lib", "t", List.of("NAME"), List.of("ID")))
                .isEqualTo("UPDATE \"LIB\".\"T\" SET \"NAME\" = ? WHERE \"ID\" = ?");
        assertThat(dialect.buildDeleteSql("lib", "t", List.of("ID")))
                .isEqualTo("DELETE FROM \"LIB\".\"T\" WHERE \"ID\" = ?");
    }
}
