package com.zeus.upload.flow;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeus.upload.domain.ColumnMapping;
import com.zeus.upload.domain.ColumnProposal;
import com.zeus.upload.domain.DbColumnMeta;
import com.zeus.upload.domain.ImportRequest;
import com.zeus.upload.domain.ParsedCsv;
import com.zeus.upload.domain.PreviewContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlowConfigurationFactoryTest {

    private final FlowConfigurationFactory factory = new FlowConfigurationFactory();

    @Test
    void shouldCreateCreateTableFlowConfiguration() {
        ImportRequest request = new ImportRequest();
        request.setLibrary("TESTLIB");
        request.setTableName("NEW_TABLE");
        request.setDropAndRecreate(true);
        request.setColumns(List.of(column("ID")));

        PreviewContext previewContext = new PreviewContext();
        ParsedCsv parsedCsv = new ParsedCsv();
        parsedCsv.getOriginalHeaders().add("id");
        previewContext.setParsedCsv(parsedCsv);

        FlowConfiguration configuration = factory.fromImportContext(request, previewContext);

        assertThat(configuration.getSource()).isInstanceOf(CsvSourceConfiguration.class);
        assertThat(configuration.getTarget()).isInstanceOf(DbTableTargetConfiguration.class);

        DbTableTargetConfiguration target = (DbTableTargetConfiguration) configuration.getTarget();
        assertThat(target.getLibrary()).isEqualTo("TESTLIB");
        assertThat(target.getTableName()).isEqualTo("NEW_TABLE");
        assertThat(target.getWriteMode()).isEqualTo(DbTableWriteMode.CREATE_TABLE);
        assertThat(target.isDropAndRecreate()).isTrue();
        assertThat(target.getColumns()).hasSize(1);
    }

    @Test
    void shouldCreateExistingTableUpsertFlowConfiguration() {
        ImportRequest request = existingRequest("UPSERT");
        request.setUpsertEnabled(true);

        FlowConfiguration configuration = factory.fromImportContext(request, existingPreview());

        DbTableTargetConfiguration target = (DbTableTargetConfiguration) configuration.getTarget();
        assertThat(target.getTableName()).isEqualTo("PERSON");
        assertThat(target.getWriteMode()).isEqualTo(DbTableWriteMode.UPSERT_EXISTING);
        assertThat(target.getMappings()).hasSize(1);
        assertThat(target.getKeyColumns()).containsExactly("ID");
        assertThat(target.getDbColumns()).hasSize(1);
    }

    @Test
    void shouldCreateExistingTableUpdateFlowConfiguration() {
        FlowConfiguration configuration = factory.fromImportContext(existingRequest("UPDATE"), existingPreview());
        DbTableTargetConfiguration target = (DbTableTargetConfiguration) configuration.getTarget();
        assertThat(target.getWriteMode()).isEqualTo(DbTableWriteMode.UPDATE_EXISTING);
        assertThat(target.getKeyColumns()).containsExactly("ID");
    }

    @Test
    void shouldCreateExistingTableDeleteFlowConfiguration() {
        FlowConfiguration configuration = factory.fromImportContext(existingRequest("DELETE"), existingPreview());
        DbTableTargetConfiguration target = (DbTableTargetConfiguration) configuration.getTarget();
        assertThat(target.getWriteMode()).isEqualTo(DbTableWriteMode.DELETE_EXISTING);
        assertThat(target.getKeyColumns()).containsExactly("ID");
    }

    private ImportRequest existingRequest(String operation) {
        ImportRequest request = new ImportRequest();
        request.setLibrary("TESTLIB");
        request.setTableName("TMP_TABLE");
        request.setUseExistingTable(true);
        request.setExistingTableName("PERSON");
        request.setOperation(operation);
        request.setMappings(List.of(mapping("id", 0, "ID"), mapping("name", 1, "NAME")));
        request.setKeyColumns(List.of("ID"));
        return request;
    }

    private PreviewContext existingPreview() {
        PreviewContext previewContext = new PreviewContext();
        ParsedCsv parsedCsv = new ParsedCsv();
        parsedCsv.getOriginalHeaders().addAll(List.of("id", "name"));
        previewContext.setParsedCsv(parsedCsv);
        previewContext.setDbColumns(List.of(
                new DbColumnMeta("ID", "INTEGER", java.sql.Types.INTEGER, 10, 10, 0, false, null, 1),
                new DbColumnMeta("NAME", "VARCHAR", java.sql.Types.VARCHAR, 64, 64, 0, true, null, 2)
        ));
        return previewContext;
    }

    private ColumnProposal column(String finalName) {
        ColumnProposal proposal = new ColumnProposal();
        proposal.setIndex(0);
        proposal.setOriginalName(finalName);
        proposal.setSanitizedName(finalName);
        proposal.setFinalName(finalName);
        proposal.setSqlType("INTEGER");
        return proposal;
    }

    private ColumnMapping mapping(String csvColumn, int csvIndex, String targetColumn) {
        ColumnMapping mapping = new ColumnMapping();
        mapping.setCsvColumn(csvColumn);
        mapping.setCsvIndex(csvIndex);
        mapping.setTargetColumn(targetColumn);
        return mapping;
    }
}
