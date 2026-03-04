package com.zeus.upload.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.zeus.upload.config.AppProperties;
import com.zeus.upload.domain.ColumnMapping;
import com.zeus.upload.domain.ColumnProposal;
import com.zeus.upload.domain.DbColumnMeta;
import com.zeus.upload.domain.ParsedCsv;
import com.zeus.upload.service.CsvParsingService;
import com.zeus.upload.service.ImportService;
import com.zeus.upload.service.MappingService;
import com.zeus.upload.service.MetadataService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UploadController.class)
class UploadControllerExistingTableTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CsvParsingService csvParsingService;

    @MockBean
    private ImportService importService;

    @MockBean
    private MetadataService metadataService;

    @MockBean
    private MappingService mappingService;

    @MockBean
    private AppProperties appProperties;

    @Test
    void uploadShouldRenderPreviewWithMappingsForExistingTableMode() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.csv",
                "text/csv",
                "first_name\nAlice".getBytes()
        );
        ParsedCsv parsedCsv = parsedCsv();
        List<DbColumnMeta> dbColumns = List.of(
                new DbColumnMeta("FIRST_NAME", "VARCHAR", java.sql.Types.VARCHAR, 100, 100, 0, true, null, 1)
        );
        List<ColumnMapping> mappings = List.of(mapping("first_name", 0, "FIRST_NAME"));

        when(csvParsingService.parse(any())).thenReturn(parsedCsv);
        when(metadataService.listColumns("BIB", "PERSON")).thenReturn(dbColumns);
        when(mappingService.autoMap(any(ParsedCsv.class), anyList())).thenReturn(mappings);
        when(appProperties.getDefaultLibrary()).thenReturn("BIB");

        mockMvc.perform(multipart("/upload")
                        .file(file)
                        .param("library", "BIB")
                        .param("tableName", "TMP_PERSON")
                        .param("useExistingTable", "true")
                        .param("existingTableName", "PERSON"))
                .andExpect(status().isOk())
                .andExpect(view().name("preview"))
                .andExpect(model().attributeExists("dbColumns"))
                .andExpect(model().attributeExists("mappings"))
                .andExpect(model().attributeExists("importRequest"));
    }

    private ParsedCsv parsedCsv() {
        ParsedCsv parsedCsv = new ParsedCsv();
        parsedCsv.getOriginalHeaders().add("first_name");
        parsedCsv.getRows().add(List.of("Alice"));
        parsedCsv.getPreviewRows().add(List.of("Alice"));
        parsedCsv.getProposals().add(columnProposal());
        return parsedCsv;
    }

    private ColumnProposal columnProposal() {
        ColumnProposal proposal = new ColumnProposal();
        proposal.setIndex(0);
        proposal.setOriginalName("first_name");
        proposal.setSanitizedName("FIRST_NAME");
        proposal.setFinalName("FIRST_NAME");
        proposal.setDetectedType("VARCHAR");
        proposal.setSqlType("VARCHAR");
        proposal.setLength(100);
        proposal.setNullable(true);
        return proposal;
    }

    private ColumnMapping mapping(String csvColumn, int csvIndex, String targetColumn) {
        ColumnMapping mapping = new ColumnMapping();
        mapping.setCsvColumn(csvColumn);
        mapping.setCsvIndex(csvIndex);
        mapping.setTargetColumn(targetColumn);
        mapping.setIgnored(false);
        return mapping;
    }
}
