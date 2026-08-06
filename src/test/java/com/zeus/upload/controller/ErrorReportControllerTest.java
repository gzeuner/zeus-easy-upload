package com.zeus.upload.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeus.upload.domain.ImportResult;
import com.zeus.upload.domain.ParseError;
import org.junit.jupiter.api.Test;

class ErrorReportControllerTest {

    @Test
    void shouldExportErrorsAsCsvAndJson() throws Exception {
        ImportResult result = ImportResult.failure(
                "failed", "", java.util.List.of(new ParseError(2, "NAME", "A,lice", "Invalid value")));
        ErrorReportController controller = new ErrorReportController(new ObjectMapper());

        String csv = new String(controller.csv(result).getBody(), java.nio.charset.StandardCharsets.UTF_8);
        String json = new String(controller.json(result).getBody(), java.nio.charset.StandardCharsets.UTF_8);

        assertThat(csv).contains("row,column,value,message").contains("\"A,lice\"");
        assertThat(json).contains("\"rowNumber\":2").contains("\"message\":\"Invalid value\"");
    }
}
