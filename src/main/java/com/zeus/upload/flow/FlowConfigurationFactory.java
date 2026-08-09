package com.zeus.upload.flow;

import com.zeus.upload.domain.ImportRequest;
import com.zeus.upload.domain.PreviewContext;
import org.springframework.stereotype.Component;

@Component
public class FlowConfigurationFactory {

    public FlowConfiguration fromImportContext(ImportRequest importRequest, PreviewContext previewContext) {
        CsvSourceConfiguration source = new CsvSourceConfiguration(previewContext.getParsedCsv());
        DbTableTargetConfiguration target = new DbTableTargetConfiguration(
                importRequest.getLibrary(),
                resolveTargetTable(importRequest),
                resolveWriteMode(importRequest),
                importRequest.isDropAndRecreate(),
                importRequest.isDryRun(),
                importRequest.getConnectionProfileName(),
                importRequest.getColumns(),
                importRequest.getMappings(),
                importRequest.getKeyColumns(),
                previewContext.getDbColumns()
        );
        return new FlowConfiguration(source, target);
    }

    private DbTableWriteMode resolveWriteMode(ImportRequest importRequest) {
        if (!importRequest.isUseExistingTable()) {
            return DbTableWriteMode.CREATE_TABLE;
        }
        if (importRequest.getOperation() != null) {
            try {
                return switch (importRequest.getOperation().trim().toUpperCase()) {
                    case "UPDATE" -> DbTableWriteMode.UPDATE_EXISTING;
                    case "DELETE" -> DbTableWriteMode.DELETE_EXISTING;
                    case "UPSERT" -> DbTableWriteMode.UPSERT_EXISTING;
                    case "INSERT" -> importRequest.isUpsertEnabled()
                            ? DbTableWriteMode.UPSERT_EXISTING
                            : DbTableWriteMode.INSERT_EXISTING;
                    default -> DbTableWriteMode.INSERT_EXISTING;
                };
            } catch (IllegalArgumentException ignored) {
                // Fall through to the legacy upsert flag.
            }
        }
        if (importRequest.isUpsertEnabled()) {
            return DbTableWriteMode.UPSERT_EXISTING;
        }
        return DbTableWriteMode.INSERT_EXISTING;
    }

    private String resolveTargetTable(ImportRequest importRequest) {
        if (importRequest.isUseExistingTable() && importRequest.getExistingTableName() != null
                && !importRequest.getExistingTableName().isBlank()) {
            return importRequest.getExistingTableName();
        }
        return importRequest.getTableName();
    }
}
