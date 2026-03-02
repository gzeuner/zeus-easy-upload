package com.zeus.upload.service;

import com.zeus.upload.domain.ParseError;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ImportException extends RuntimeException {

    private final List<ParseError> errors;

    public ImportException(String message, List<ParseError> errors, Throwable cause) {
        super(message, cause);
        this.errors = new ArrayList<>(errors);
    }

    public ImportException(String message, List<ParseError> errors) {
        super(message);
        this.errors = new ArrayList<>(errors);
    }

    public List<ParseError> getErrors() {
        return Collections.unmodifiableList(errors);
    }
}
