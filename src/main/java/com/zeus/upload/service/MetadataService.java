package com.zeus.upload.service;

import com.zeus.upload.domain.DbColumnMeta;
import com.zeus.upload.domain.DbTableRef;
import java.util.List;

public interface MetadataService {

    List<DbTableRef> listTables(String library);

    List<DbColumnMeta> listColumns(String library, String tableName);
}
