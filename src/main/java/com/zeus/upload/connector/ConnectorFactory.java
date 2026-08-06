package com.zeus.upload.connector;

import com.zeus.upload.connector.db.DbTableTargetConnector;
import com.zeus.upload.connector.file.CsvSourceConnector;
import com.zeus.upload.connector.file.CsvTargetConnector;
import com.zeus.upload.flow.CsvSourceConfiguration;
import com.zeus.upload.flow.CsvTargetConfiguration;
import com.zeus.upload.flow.DbTableTargetConfiguration;
import com.zeus.upload.flow.SourceConfiguration;
import com.zeus.upload.flow.TargetConfiguration;
import com.zeus.upload.service.ImportService;
import org.springframework.stereotype.Component;

@Component
public class ConnectorFactory {

    private final ImportService importService;

    public ConnectorFactory(ImportService importService) {
        this.importService = importService;
    }

    public SourceConnector createSource(SourceConfiguration configuration) {
        if (configuration instanceof CsvSourceConfiguration csvSourceConfiguration) {
            return new CsvSourceConnector(csvSourceConfiguration.getParsedCsv());
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
        throw new IllegalArgumentException("Unsupported target configuration: " + configuration.getClass().getName());
    }
}
