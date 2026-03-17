package com.zeus.upload.flow;

import com.zeus.upload.domain.ParsedCsv;
import java.util.Objects;

public class CsvSourceConfiguration implements SourceConfiguration {

    private final ParsedCsv parsedCsv;

    public CsvSourceConfiguration(ParsedCsv parsedCsv) {
        this.parsedCsv = Objects.requireNonNull(parsedCsv, "parsedCsv must not be null");
    }

    public ParsedCsv getParsedCsv() {
        return parsedCsv;
    }
}
