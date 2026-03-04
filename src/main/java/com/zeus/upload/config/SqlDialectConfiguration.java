package com.zeus.upload.config;

import com.zeus.upload.sql.Db2iDialect;
import com.zeus.upload.sql.H2Dialect;
import com.zeus.upload.sql.SqlDialect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class SqlDialectConfiguration {

    @Bean
    @Profile("!test")
    public SqlDialect db2iDialect() {
        return new Db2iDialect();
    }

    @Bean
    @Profile("test")
    public SqlDialect h2Dialect() {
        return new H2Dialect();
    }
}
