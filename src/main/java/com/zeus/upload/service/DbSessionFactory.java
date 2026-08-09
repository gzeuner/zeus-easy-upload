package com.zeus.upload.service;

import com.zeus.upload.domain.ConnectionProfile;
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
 * encrypted connection profile (pooled via {@link ConnectionPoolCache}).
 * Credentials never leave this factory.
 */
@Service
public class DbSessionFactory {

    private static final Logger log = LoggerFactory.getLogger(DbSessionFactory.class);

    private final DataSource bootstrapDataSource;
    private final SqlDialect bootstrapDialect;
    private final ConnectionProfileService connectionProfileService;
    private final ConnectionPoolCache connectionPoolCache;
    private final SqlDialectRegistry dialectRegistry;

    public DbSessionFactory(
            DataSource bootstrapDataSource,
            SqlDialect bootstrapDialect,
            ConnectionProfileService connectionProfileService,
            ConnectionPoolCache connectionPoolCache,
            SqlDialectRegistry dialectRegistry
    ) {
        this.bootstrapDataSource = Objects.requireNonNull(bootstrapDataSource);
        this.bootstrapDialect = Objects.requireNonNull(bootstrapDialect);
        this.connectionProfileService = Objects.requireNonNull(connectionProfileService);
        this.connectionPoolCache = Objects.requireNonNull(connectionPoolCache);
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
     * Named sessions share a cached Hikari pool; session close does not shut the pool down.
     */
    public DbSession open(String connectionProfileName) {
        if (!StringUtils.hasText(connectionProfileName)) {
            return openDefault();
        }
        String name = connectionProfileName.trim();
        try {
            ConnectionProfile profile = connectionProfileService.load(name);
            if (profile.getType() == null || !profile.getType().isJdbc()) {
                throw new IllegalArgumentException(
                        "Connection profile '" + name + "' is not a JDBC profile; select DB2_400 or POSTGRES.");
            }
            Map<String, String> credentials = connectionProfileService.loadCredentials(name);
            DataSource dataSource = connectionPoolCache.getOrCreate(profile, credentials);
            SqlDialect dialect = resolveDialect(profile);
            log.debug("Opened DB session for connection profile '{}' (product={}, pooled=true)", name, dialect.product());
            // closer=null: pool is owned by ConnectionPoolCache, not the session.
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
        return dialectRegistry.resolveFromConnectionType(profile.getType());
    }
}
