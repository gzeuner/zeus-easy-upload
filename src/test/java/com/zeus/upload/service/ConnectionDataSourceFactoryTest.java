package com.zeus.upload.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeus.upload.domain.ConnectionProfile;
import com.zeus.upload.domain.ConnectionType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ConnectionDataSourceFactoryTest {

    private final ConnectionDataSourceFactory factory = new ConnectionDataSourceFactory();

    @Test
    void buildsJdbcDataSourceFromProfileCredentials() {
        ConnectionProfile profile = new ConnectionProfile(
                "h2-local", ConnectionType.DB2_400, "jdbc:h2:mem:factory_test", null, true, null);
        DriverManagerDataSource ds = (DriverManagerDataSource) factory.create(
                profile, Map.of("username", "sa", "secret", ""));

        assertThat(ds.getUrl()).isEqualTo("jdbc:h2:mem:factory_test");
        assertThat(ds.getUsername()).isEqualTo("sa");
        assertThat(ConnectionDataSourceFactory.resolveDriverClass(ds.getUrl())).isEqualTo("org.h2.Driver");
    }

    @Test
    void rejectsRestProfiles() {
        ConnectionProfile profile = new ConnectionProfile(
                "api", ConnectionType.REST, "https://example.com", null, true, null);
        assertThatThrownBy(() -> factory.create(profile, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a JDBC profile");
    }

    @Test
    void resolvesDriversFromUrl() {
        assertThat(ConnectionDataSourceFactory.resolveDriverClass("jdbc:as400://h/l"))
                .isEqualTo("com.ibm.as400.access.AS400JDBCDriver");
        assertThat(ConnectionDataSourceFactory.resolveDriverClass("jdbc:postgresql://h/db"))
                .isEqualTo("org.postgresql.Driver");
    }
}
