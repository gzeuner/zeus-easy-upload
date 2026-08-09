package com.zeus.upload.connector.db;

import com.zeus.upload.connector.ImportResultAwareTargetConnector;
import com.zeus.upload.domain.ImportRequest;
import com.zeus.upload.domain.ImportResult;
import com.zeus.upload.domain.ParsedCsv;
import com.zeus.upload.flow.DbTableTargetConfiguration;
import com.zeus.upload.flow.DbTableWriteMode;
import com.zeus.upload.flow.DataRecord;
import com.zeus.upload.service.ImportService;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class DbTableTargetConnector implements ImportResultAwareTargetConnector {

    private final ImportService importService;
    private final DbTableTargetConfiguration configuration;

    private ImportResult result;

    public DbTableTargetConnector(ImportService importService, DbTableTargetConfiguration configuration) {
        this.importService = Objects.requireNonNull(importService, "importService must not be null");
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
    }

    @Override
    public void write(Stream<DataRecord> records) {
        ParsedCsv parsedCsv = new ParsedCsv();
        records.map(this::toRow).forEach(parsedCsv.getRows()::add);
        String connection = configuration.getConnectionProfileName();

        if (configuration.getWriteMode() == DbTableWriteMode.CREATE_TABLE) {
            result = importService.importCsv(createImportRequest(), parsedCsv);
            return;
        }

        if (configuration.getWriteMode() == DbTableWriteMode.UPSERT_EXISTING) {
            result = importService.upsertIntoExistingTable(
                    connection,
                    configuration.getLibrary(),
                    configuration.getTableName(),
                    parsedCsv,
                    configuration.getDbColumns(),
                    configuration.getMappings(),
                    configuration.getKeyColumns(),
                    configuration.isDryRun()
            );
            return;
        }

        if (configuration.getWriteMode() == DbTableWriteMode.UPDATE_EXISTING) {
            result = importService.updateIntoExistingTable(
                    connection,
                    configuration.getLibrary(), configuration.getTableName(), parsedCsv,
                    configuration.getDbColumns(), configuration.getMappings(),
                    configuration.getKeyColumns(), configuration.isDryRun());
            return;
        }

        if (configuration.getWriteMode() == DbTableWriteMode.DELETE_EXISTING) {
            result = importService.deleteFromExistingTable(
                    connection,
                    configuration.getLibrary(), configuration.getTableName(), parsedCsv,
                    configuration.getDbColumns(), configuration.getMappings(),
                    configuration.getKeyColumns(), configuration.isDryRun());
            return;
        }

        result = importService.importIntoExistingTable(
                connection,
                configuration.getLibrary(),
                configuration.getTableName(),
                parsedCsv,
                configuration.getDbColumns(),
                configuration.getMappings(),
                configuration.isDryRun()
        );
    }

    @Override
    public ImportResult getImportResult() {
        return result;
    }

    public ImportResult getResult() {
        return getImportResult();
    }

    private List<String> toRow(DataRecord record) {
        return record.asMap().values().stream()
                .map(value -> value == null ? null : String.valueOf(value))
                .toList();
    }

    private ImportRequest createImportRequest() {
        ImportRequest importRequest = new ImportRequest();
        importRequest.setLibrary(configuration.getLibrary());
        importRequest.setTableName(configuration.getTableName());
        importRequest.setDropAndRecreate(configuration.isDropAndRecreate());
        importRequest.setDryRun(configuration.isDryRun());
        importRequest.setColumns(configuration.getColumns());
        importRequest.setConnectionProfileName(configuration.getConnectionProfileName());
        return importRequest;
    }
}
