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
import com.zeus.upload.service.ImportService;
import com.zeus.upload.sql.SqlDialect;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class ConnectorFactory {

    private final ImportService importService;
    private final DataSource dataSource;
    private final SqlDialect sqlDialect;

    public ConnectorFactory(ImportService importService) {
        this(importService, null, null);
    }

    @Autowired
    public ConnectorFactory(ImportService importService, DataSource dataSource, SqlDialect sqlDialect) {
        this.importService = importService;
        this.dataSource = dataSource;
        this.sqlDialect = sqlDialect;
    }

    public SourceConnector createSource(SourceConfiguration configuration) {
        if (configuration instanceof CsvSourceConfiguration csvSourceConfiguration) {
            return new CsvSourceConnector(csvSourceConfiguration.getParsedCsv());
        }
        if (configuration instanceof DbTableSourceConfiguration dbSourceConfiguration) {
            return new DbTableSourceConnector(dataSource, sqlDialect, dbSourceConfiguration);
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
}
