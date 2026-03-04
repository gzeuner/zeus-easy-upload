package com.zeus.upload.sql;

public interface SqlDialect {

    String quoteIdentifier(String identifier);

    String qualifyTable(String libraryOrSchema, String table);
}
