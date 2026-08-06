package com.zeus.upload.flow;

public enum DbTableWriteMode {
    CREATE_TABLE,
    INSERT_EXISTING,
    UPDATE_EXISTING,
    DELETE_EXISTING,
    UPSERT_EXISTING
}
