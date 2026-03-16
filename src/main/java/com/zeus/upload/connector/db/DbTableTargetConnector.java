package com.zeus.upload.connector.db;

import com.zeus.upload.connector.TargetConnector;
import com.zeus.upload.domain.DbColumnMeta;
import com.zeus.upload.domain.ImportRequest;
import com.zeus.upload.domain.ImportResult;
import com.zeus.upload.domain.ParsedCsv;
import com.zeus.upload.flow.DataRecord;
import com.zeus.upload.service.ImportService;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class DbTableTargetConnector implements TargetConnector {

    private final ImportService importService;
    private final ImportRequest importRequest;
    private final List<DbColumnMeta> dbColumns;

    private ImportResult result;

    public DbTableTargetConnector(ImportService importService, ImportRequest importRequest, List<DbColumnMeta> dbColumns) {
        this.importService = Objects.requireNonNull(importService, "importService must not be null");
        this.importRequest = Objects.requireNonNull(importRequest, "importRequest must not be null");
        this.dbColumns = dbColumns == null ? List.of() : List.copyOf(dbColumns);
    }

    @Override
    public void write(Stream<DataRecord> records) {
        ParsedCsv parsedCsv = new ParsedCsv();
        records.map(this::toRow).forEach(parsedCsv.getRows()::add);

        if (!importRequest.isUseExistingTable()) {
            result = importService.importCsv(importRequest, parsedCsv);
            return;
        }

        if (importRequest.isUpsertEnabled()) {
            result = importService.upsertIntoExistingTable(
                    importRequest.getLibrary(),
                    importRequest.getExistingTableName(),
                    parsedCsv,
                    dbColumns,
                    importRequest.getMappings(),
                    importRequest.getKeyColumns()
            );
            return;
        }

        result = importService.importIntoExistingTable(
                importRequest.getLibrary(),
                importRequest.getExistingTableName(),
                parsedCsv,
                dbColumns,
                importRequest.getMappings()
        );
    }

    public ImportResult getResult() {
        return result;
    }

    private List<String> toRow(DataRecord record) {
        return record.asMap().values().stream()
                .map(value -> value == null ? null : String.valueOf(value))
                .toList();
    }
}
