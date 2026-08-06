package com.zeus.upload.flow;

import java.nio.file.Path;
import java.util.Objects;

public class CsvTargetConfiguration implements TargetConfiguration {

    private final Path outputFile;
    private final char delimiter;
    private final char quote;

    public CsvTargetConfiguration(Path outputFile, char delimiter, char quote) {
        this.outputFile = Objects.requireNonNull(outputFile, "outputFile must not be null");
        this.delimiter = delimiter;
        this.quote = quote;
    }

    public Path getOutputFile() { return outputFile; }
    public char getDelimiter() { return delimiter; }
    public char getQuote() { return quote; }
}
