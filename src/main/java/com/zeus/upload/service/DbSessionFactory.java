package com.zeus.upload.service;

import com.zeus.upload.domain.ConnectionProfile;
import com.zeus.upload.domain.ConnectionType;
import com.zeus.upload.sql.DatabaseProduct;
import com.zeus.upload.sql.SqlDialect;
import com.zeus.upload.sql.SqlDialectRegistry;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Opens {@link DbSession}s against the bootstrap Spring DataSource or a named
 * encrypted connection profile. Credentials never leave this factory.
 */
@Service
public class DbSessionFactory {

    private static final Logger log = LoggerFactory.getLogger(DbSessionFactory.class);

    private final DataSource bootstrapDataSource;
    private final SqlDialect bootstrapDialect;
    private final ConnectionProfileService connectionProfileService;
    private final ConnectionDataSourceFactory dataSourceFactory;
    private final SqlDialectRegistry dialectRegistry;

    public DbSessionFactory(
            DataSource bootstrapDataSource,
            SqlDialect bootstrapDialect,
            ConnectionProfileService connectionProfileService,
            ConnectionDataSourceFactory dataSourceFactory,
            SqlDialectRegistry dialectRegistry
    ) {
        this.bootstrapDataSource = Objects.requireNonNull(bootstrapDataSource);
        this.bootstrapDialect = Objects.requireNonNull(bootstrapDialect);
        this.connectionProfileService = Objects.requireNonNull(connectionProfileService);
        this.dataSourceFactory = Objects.requireNonNull(dataSourceFactory);
        this.dialectRegistry = Objects.requireNonNull(dialectRegistry);
    }

    /** Bootstrap DataSource + default dialect (Spring profile). */
    public DbSession openDefault() {
        return new DbSession(
                bootstrapDataSource,
                bootstrapDialect,
                null,
                bootstrapDialect.product(),
                null
        );
    }

    /**
     * Resolve by optional profile name. Blank/null uses {@link #openDefault()}.
     */
    public DbSession open(String connectionProfileName) {
        if (!StringUtils.hasText(connectionProfileName)) {
            return openDefault();
        }
        String name = connectionProfileName.trim();
        try {
            ConnectionProfile profile = connectionProfileService.load(name);
            if (profile.getType() == ConnectionType.REST) {
                throw new IllegalArgumentException(
                        "Connection profile '" + name + "' is REST; select a JDBC/DB2 profile for database operations.");
            }
            Map<String, String> credentials = connectionProfileService.loadCredentials(name);
            DataSource dataSource = dataSourceFactory.create(profile, credentials);
            SqlDialect dialect = resolveDialect(profile);
            log.info("Opened DB session for connection profile '{}' (product={})", name, dialect.product());
            return new DbSession(dataSource, dialect, name, dialect.product(), null);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not load connection profile '" + name + "': " + ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Could not open database session for connection profile '" + name + "': " + ex.getMessage(),
                    ex
            );
        }
    }

    private SqlDialect resolveDialect(ConnectionProfile profile) {
        DatabaseProduct fromUrl = SqlDialectRegistry.detectProduct(profile.getEndpoint());
        if (fromUrl != DatabaseProduct.GENERIC_JDBC) {
            return dialectRegistry.get(fromUrl);
        }
        if (profile.getType() == ConnectionType.DB2_400) {
            return dialectRegistry.get(DatabaseProduct.DB2_I);
        }
        return dialectRegistry.get(DatabaseProduct.GENERIC_JDBC);
    }
}
