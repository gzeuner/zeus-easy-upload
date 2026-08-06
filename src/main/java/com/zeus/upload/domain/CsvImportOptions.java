package com.zeus.upload.domain;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class CsvImportOptions {

    private String delimiter;
    private String encoding = StandardCharsets.UTF_8.name();
    private String quote = "\"";

    public static CsvImportOptions defaults() {
        return new CsvImportOptions();
    }

    public char delimiterOr(char fallback) {
        return delimiter == null || delimiter.isBlank() ? fallback : delimiter.charAt(0);
    }

    public char quoteOr(char fallback) {
        return quote == null || quote.isBlank() ? fallback : quote.charAt(0);
    }

    public Charset charset() {
        return Charset.forName(encoding == null || encoding.isBlank() ? StandardCharsets.UTF_8.name() : encoding);
    }

    public String getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getQuote() {
        return quote;
    }

    public void setQuote(String quote) {
        this.quote = quote;
    }
}
