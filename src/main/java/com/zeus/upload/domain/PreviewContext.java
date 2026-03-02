package com.zeus.upload.domain;

public class PreviewContext {

    private ParsedCsv parsedCsv;
    private String originalFilename;

    public ParsedCsv getParsedCsv() {
        return parsedCsv;
    }

    public void setParsedCsv(ParsedCsv parsedCsv) {
        this.parsedCsv = parsedCsv;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }
}
