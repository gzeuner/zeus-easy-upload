package com.zeus.upload.service;

import com.zeus.upload.domain.DbColumnMeta;
import com.zeus.upload.domain.DbTableRef;
import java.util.List;

public interface MetadataService {

    List<DbTableRef> listTables(String library);

    /**
     * List tables using an optional named connection profile (JDBC/DB2).
     * Blank profile uses the bootstrap DataSource.
     */
    default List<DbTableRef> listTables(String library, String connectionProfileName) {
        return listTables(library);
    }

    List<DbColumnMeta> listColumns(String library, String tableName);

    default List<DbColumnMeta> listColumns(String library, String tableName, String connectionProfileName) {
        return listColumns(library, tableName);
    }
}
