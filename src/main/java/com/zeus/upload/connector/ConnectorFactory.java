package com.zeus.upload.connector;

import com.zeus.upload.connector.db.DbTableTargetConnector;
import com.zeus.upload.connector.db.DbTableSourceConnector;
import com.zeus.upload.connector.file.CsvSourceConnector;
import com.zeus.upload.connector.file.CsvTargetConnector;
import com.zeus.upload.connector.file.FilesystemCsvSourceConnector;
import com.zeus.upload.connector.file.FilesystemCsvTargetConnector;
import com.zeus.upload.connector.rest.RestJsonSourceConnector;
import com.zeus.upload.connector.rest.RestJsonTargetConnector;
import com.zeus.upload.flow.CsvSourceConfiguration;
import com.zeus.upload.flow.CsvTargetConfiguration;
import com.zeus.upload.flow.DbTableTargetConfiguration;
import com.zeus.upload.flow.DbTableSourceConfiguration;
import com.zeus.upload.flow.FilesystemCsvSourceConfiguration;
import com.zeus.upload.flow.FilesystemCsvTargetConfiguration;
import com.zeus.upload.flow.RestSourceConfiguration;
import com.zeus.upload.flow.RestTargetConfiguration;
import com.zeus.upload.flow.SourceConfiguration;
import com.zeus.upload.flow.TargetConfiguration;
import com.zeus.upload.service.DbSession;
import com.zeus.upload.service.DbSessionFactory;
import com.zeus.upload.service.ImportService;
import com.zeus.upload.sql.SqlDialect;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ConnectorFactory {

    private final ImportService importService;
    private final DataSource dataSource;
    private final SqlDialect sqlDialect;
    private final DbSessionFactory dbSessionFactory;

    public ConnectorFactory(ImportService importService) {
        this(importService, null, null, null);
    }

    @Autowired
    public ConnectorFactory(
            ImportService importService,
            DataSource dataSource,
            SqlDialect sqlDialect,
            DbSessionFactory dbSessionFactory
    ) {
        this.importService = importService;
        this.dataSource = dataSource;
        this.sqlDialect = sqlDialect;
        this.dbSessionFactory = dbSessionFactory;
    }

    public SourceConnector createSource(SourceConfiguration configuration) {
        if (configuration instanceof CsvSourceConfiguration csvSourceConfiguration) {
            return new CsvSourceConnector(csvSourceConfiguration.getParsedCsv());
        }
        if (configuration instanceof DbTableSourceConfiguration dbSourceConfiguration) {
            return createDbTableSource(dbSourceConfiguration);
        }
        if (configuration instanceof FilesystemCsvSourceConfiguration filesystemConfiguration) {
            return new FilesystemCsvSourceConnector(filesystemConfiguration);
        }
        if (configuration instanceof RestSourceConfiguration restConfiguration) {
            return new RestJsonSourceConnector(restConfiguration);
        }
        throw new IllegalArgumentException("Unsupported source configuration: " + configuration.getClass().getName());
    }

    public TargetConnector createTarget(TargetConfiguration configuration) {
        if (configuration instanceof DbTableTargetConfiguration dbTableTargetConfiguration) {
            return new DbTableTargetConnector(importService, dbTableTargetConfiguration);
        }
        if (configuration instanceof CsvTargetConfiguration csvTargetConfiguration) {
            return new CsvTargetConnector(csvTargetConfiguration);
        }
        if (configuration instanceof FilesystemCsvTargetConfiguration filesystemConfiguration) {
            return new FilesystemCsvTargetConnector(filesystemConfiguration);
        }
        if (configuration instanceof RestTargetConfiguration restConfiguration) {
            return new RestJsonTargetConnector(restConfiguration);
        }
        throw new IllegalArgumentException("Unsupported target configuration: " + configuration.getClass().getName());
    }

    private SourceConnector createDbTableSource(DbTableSourceConfiguration configuration) {
        if (dbSessionFactory != null) {
            return () -> {
                DbSession session = dbSessionFactory.open(configuration.getConnectionProfileName());
                try {
                    return new DbTableSourceConnector(session.dataSource(), session.dialect(), configuration)
                            .read()
                            .onClose(session::close);
                } catch (RuntimeException ex) {
                    session.close();
                    throw ex;
                }
            };
        }
        Objects.requireNonNull(dataSource, "dataSource");
        Objects.requireNonNull(sqlDialect, "sqlDialect");
        return new DbTableSourceConnector(dataSource, sqlDialect, configuration);
    }
}
