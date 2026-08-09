package com.zeus.upload.config;

import com.zeus.upload.sql.DatabaseProduct;
import com.zeus.upload.sql.Db2iDialect;
import com.zeus.upload.sql.GenericJdbcDialect;
import com.zeus.upload.sql.H2Dialect;
import com.zeus.upload.sql.PostgresDialect;
import com.zeus.upload.sql.SqlDialect;
import com.zeus.upload.sql.SqlDialectRegistry;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class SqlDialectConfiguration {

    @Bean
    public SqlDialectRegistry sqlDialectRegistry() {
        Map<DatabaseProduct, SqlDialect> dialects = new EnumMap<>(DatabaseProduct.class);
        dialects.put(DatabaseProduct.DB2_I, new Db2iDialect());
        dialects.put(DatabaseProduct.H2, new H2Dialect());
        dialects.put(DatabaseProduct.POSTGRES, new PostgresDialect());
        dialects.put(DatabaseProduct.GENERIC_JDBC, new GenericJdbcDialect());
        return new SqlDialectRegistry(dialects);
    }

    /**
     * Production / IBM i default (active when neither test nor local profile is set).
     */
    @Bean
    @Profile("!test & !local")
    public SqlDialect db2iDialect(SqlDialectRegistry registry) {
        return registry.get(DatabaseProduct.DB2_I);
    }

    /**
     * Embedded H2 for unit/integration tests and offline local development.
     */
    @Bean
    @Profile({"test", "local"})
    public SqlDialect h2Dialect(SqlDialectRegistry registry) {
        return registry.get(DatabaseProduct.H2);
    }
}
