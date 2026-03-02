package com.zeus.upload.domain;

import java.util.ArrayList;
import java.util.List;

public class ParsedCsv {

    private char delimiter;
    private final List<String> originalHeaders = new ArrayList<>();
    private final List<ColumnProposal> proposals = new ArrayList<>();
    private final List<List<String>> rows = new ArrayList<>();
    private final List<List<String>> previewRows = new ArrayList<>();
    private final List<ParseError> parseErrors = new ArrayList<>();

    public char getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(char delimiter) {
        this.delimiter = delimiter;
    }

    public List<String> getOriginalHeaders() {
        return originalHeaders;
    }

    public List<ColumnProposal> getProposals() {
        return proposals;
    }

    public List<List<String>> getRows() {
        return rows;
    }

    public List<List<String>> getPreviewRows() {
        return previewRows;
    }

    public List<ParseError> getParseErrors() {
        return parseErrors;
    }
}
