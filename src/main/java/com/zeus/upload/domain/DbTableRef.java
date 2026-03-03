package com.zeus.upload.domain;

import java.util.Objects;

public class DbTableRef {

    private final String library;
    private final String tableName;

    public DbTableRef(String library, String tableName) {
        this.library = library;
        this.tableName = tableName;
    }

    public String getLibrary() {
        return library;
    }

    public String getTableName() {
        return tableName;
    }

    @Override
    public String toString() {
        return "DbTableRef{"
                + "library='" + library + '\''
                + ", tableName='" + tableName + '\''
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DbTableRef that)) {
            return false;
        }
        return Objects.equals(library, that.library) && Objects.equals(tableName, that.tableName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(library, tableName);
    }
}
