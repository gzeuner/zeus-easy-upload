package com.zeus.upload.sql;

import com.zeus.upload.util.Db2IdentifierUtil;

public class Db2iDialect implements SqlDialect {

    @Override
    public String quoteIdentifier(String identifier) {
        String sanitized = Db2IdentifierUtil.sanitizeIdentifier(identifier);
        return "\"" + sanitized.replace("\"", "\"\"") + "\"";
    }

    @Override
    public String qualifyTable(String libraryOrSchema, String table) {
        return quoteIdentifier(libraryOrSchema) + "." + quoteIdentifier(table);
    }
}
