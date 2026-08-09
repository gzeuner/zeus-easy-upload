package com.zeus.upload.sql;

/**
 * IBM i DB2/400 dialect. Libraries are not auto-created; they must exist on the system.
 * Upsert uses classic DB2 MERGE … USING (VALUES …) syntax.
 */
public class Db2iDialect extends AbstractSqlDialect {

    private final MergeSqlBuilder mergeSqlBuilder = new MergeSqlBuilder(this);

    @Override
    public DatabaseProduct product() {
        return DatabaseProduct.DB2_I;
    }

    @Override
    public IdentifierPolicy identifierPolicy() {
        return IdentifierPolicy.ibmISystemNames();
    }

    @Override
    public UpsertStrategy upsertStrategy() {
        return UpsertStrategy.DB2_MERGE_VALUES;
    }

    @Override
    public UpsertSql buildUpsertSql(
            String libraryOrSchema,
            String table,
            java.util.List<String> insertColumns,
            java.util.List<String> updateColumns,
            java.util.List<String> keyColumns
    ) {
        String sql = mergeSqlBuilder.buildDb2MergeSql(
                libraryOrSchema, table, insertColumns, updateColumns, keyColumns);
        return new UpsertSql(sql, UpsertStrategy.DB2_MERGE_VALUES);
    }
}
