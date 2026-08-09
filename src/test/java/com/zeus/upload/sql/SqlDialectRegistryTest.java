package com.zeus.upload.sql;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeus.upload.domain.ConnectionType;
import org.junit.jupiter.api.Test;

class SqlDialectRegistryTest {

    private final SqlDialectRegistry registry = SqlDialectRegistry.createDefault();

    @Test
    void detectsProductsFromJdbcUrl() {
        assertThat(SqlDialectRegistry.detectProduct("jdbc:as400://host/LIB;translate binary=true"))
                .isEqualTo(DatabaseProduct.DB2_I);
        assertThat(SqlDialectRegistry.detectProduct("jdbc:h2:mem:test;MODE=DB2"))
                .isEqualTo(DatabaseProduct.H2);
        assertThat(SqlDialectRegistry.detectProduct("jdbc:postgresql://localhost:5432/app"))
                .isEqualTo(DatabaseProduct.POSTGRES);
        assertThat(SqlDialectRegistry.detectProduct("jdbc:mysql://localhost/db"))
                .isEqualTo(DatabaseProduct.GENERIC_JDBC);
        assertThat(SqlDialectRegistry.detectProduct(null))
                .isEqualTo(DatabaseProduct.GENERIC_JDBC);
    }

    @Test
    void resolvesDialects() {
        assertThat(registry.resolveFromJdbcUrl("jdbc:as400://x/y").product()).isEqualTo(DatabaseProduct.DB2_I);
        assertThat(registry.resolveFromJdbcUrl("jdbc:h2:file:./.local/h2/zeus").product()).isEqualTo(DatabaseProduct.H2);
        assertThat(registry.resolveFromJdbcUrl("jdbc:postgresql://x/y").product()).isEqualTo(DatabaseProduct.POSTGRES);
        assertThat(registry.resolveFromConnectionType(ConnectionType.DB2_400).product()).isEqualTo(DatabaseProduct.DB2_I);
        assertThat(registry.resolveFromConnectionType(ConnectionType.REST).product()).isEqualTo(DatabaseProduct.GENERIC_JDBC);
    }
}
