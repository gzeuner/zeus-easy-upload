package com.zeus.upload.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeus.upload.domain.ImportResult;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.SessionAttribute;

@Controller
public class ErrorReportController {

    private final ObjectMapper objectMapper;

    public ErrorReportController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @GetMapping("/result/errors.csv")
    public ResponseEntity<byte[]> csv(@SessionAttribute("lastImportResult") ImportResult result) {
        StringBuilder csv = new StringBuilder("row,column,value,message\r\n");
        result.getErrors().forEach(error -> csv.append(String.join(",",
                escape(String.valueOf(error.getRowNumber())),
                escape(error.getColumnName()),
                escape(error.getRawValue()),
                escape(error.getMessage()))).append("\r\n"));
        return download(csv.toString(), "import-errors.csv", "text/csv");
    }

    @GetMapping("/result/errors.json")
    public ResponseEntity<byte[]> json(@SessionAttribute("lastImportResult") ImportResult result)
            throws JsonProcessingException {
        return download(objectMapper.writeValueAsString(result.getErrors()), "import-errors.json", "application/json");
    }

    private ResponseEntity<byte[]> download(String content, String filename, String mediaType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mediaType));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(content.getBytes(StandardCharsets.UTF_8));
    }

    private String escape(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
