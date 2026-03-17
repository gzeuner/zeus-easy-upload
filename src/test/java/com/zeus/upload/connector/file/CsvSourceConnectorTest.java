package com.zeus.upload.connector.file;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeus.upload.config.AppProperties;
import com.zeus.upload.domain.ParsedCsv;
import com.zeus.upload.service.CsvParsingService;
import com.zeus.upload.service.TypeInferenceService;
import com.zeus.upload.util.ColumnNameSanitizer;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

class CsvSourceConnectorTest {

    @Test
    void shouldEmitOrderedRecordsFromParsedCsv() throws IOException {
        CsvParsingService csvParsingService = new CsvParsingService(
                new TypeInferenceService(),
                new ColumnNameSanitizer(),
                new AppProperties()
        );
        ParsedCsv parsedCsv = csvParsingService.parse(sampleCsv());

        List<com.zeus.upload.flow.DataRecord> records = new CsvSourceConnector(parsedCsv).read().toList();

        assertThat(records).hasSize(3);
        assertThat(List.copyOf(records.get(0).asMap().keySet()))
                .containsExactlyElementsOf(parsedCsv.getProposals().stream().map(p -> p.getFinalName()).toList());
        assertThat(List.copyOf(records.get(0).asMap().values()))
                .containsExactly("1", "Alice", "123.45", "2025-01-20", "2025-01-20 10:15:30");
    }

    private MockMultipartFile sampleCsv() throws IOException {
        ClassPathResource resource = new ClassPathResource("examples/sample.csv");
        return new MockMultipartFile(
                "file",
                "sample.csv",
                "text/csv",
                resource.getInputStream()
        );
    }
}
