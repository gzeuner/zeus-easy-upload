package com.zeus.upload.flow;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;

public class FilesystemCsvSourceConfiguration implements SourceConfiguration {
    private final Path rootDirectory;
    private final String relativeFile;
    private final Charset charset;
    private final char delimiter;
    private final char quote;

    public FilesystemCsvSourceConfiguration(Path rootDirectory, String relativeFile) {
        this(rootDirectory, relativeFile, StandardCharsets.UTF_8, ',', '"');
    }

    public FilesystemCsvSourceConfiguration(Path rootDirectory, String relativeFile, Charset charset,
                                            char delimiter, char quote) {
        this.rootDirectory = Objects.requireNonNull(rootDirectory, "rootDirectory must not be null");
        this.relativeFile = requireRelativeFile(relativeFile);
        this.charset = Objects.requireNonNull(charset, "charset must not be null");
        this.delimiter = delimiter;
        this.quote = quote;
    }

    private String requireRelativeFile(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("relativeFile must not be blank");
        if (Path.of(value).isAbsolute()) throw new IllegalArgumentException("Filesystem connector requires a relative file path");
        return value;
    }

    public Path getRootDirectory() { return rootDirectory; }
    public String getRelativeFile() { return relativeFile; }
    public Charset getCharset() { return charset; }
    public char getDelimiter() { return delimiter; }
    public char getQuote() { return quote; }
}
