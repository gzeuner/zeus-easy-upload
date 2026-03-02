package com.zeus.upload.domain;

import java.util.ArrayList;
import java.util.List;

public class ImportResult {

    private boolean success;
    private String message;
    private String createTableSql;
    private int insertedRows;
    private final List<ParseError> errors = new ArrayList<>();

    public static ImportResult success(String message, String createTableSql, int insertedRows) {
        ImportResult result = new ImportResult();
        result.success = true;
        result.message = message;
        result.createTableSql = createTableSql;
        result.insertedRows = insertedRows;
        return result;
    }

    public static ImportResult failure(String message, String createTableSql, List<ParseError> errors) {
        ImportResult result = new ImportResult();
        result.success = false;
        result.message = message;
        result.createTableSql = createTableSql;
        result.errors.addAll(errors);
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getCreateTableSql() {
        return createTableSql;
    }

    public int getInsertedRows() {
        return insertedRows;
    }

    public List<ParseError> getErrors() {
        return errors;
    }
}
