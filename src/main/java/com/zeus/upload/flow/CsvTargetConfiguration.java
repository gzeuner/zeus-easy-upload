package com.zeus.upload.flow;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class CsvTargetConfiguration implements TargetConfiguration {

    private final Path outputFile;
    private final Charset charset;
    private final char delimiter;
    private final char quote;
    private final List<String> headers;

    public CsvTargetConfiguration(Path outputFile, char delimiter, char quote) {
        this(outputFile, delimiter, quote, StandardCharsets.UTF_8, List.of());
    }

    public CsvTargetConfiguration(Path outputFile, char delimiter, char quote, List<String> headers) {
        this(outputFile, delimiter, quote, StandardCharsets.UTF_8, headers);
    }

    public CsvTargetConfiguration(Path outputFile, char delimiter, char quote, Charset charset, List<String> headers) {
        this.outputFile = Objects.requireNonNull(outputFile, "outputFile must not be null");
        this.delimiter = delimiter;
        this.charset = Objects.requireNonNull(charset, "charset must not be null");
        this.quote = quote;
        this.headers = headers == null ? List.of() : List.copyOf(headers);
    }

    public Path getOutputFile() { return outputFile; }
    public char getDelimiter() { return delimiter; }
    public Charset getCharset() { return charset; }
    public char getQuote() { return quote; }
    public List<String> getHeaders() { return headers; }
}
