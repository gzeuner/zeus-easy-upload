# SQL dialects and multi-database support

zeus-easy-upload talks to databases through **JDBC + `SqlDialect`**, not through an ORM.
Dynamic CREATE TABLE, batch DML, streaming SELECT and metadata do not map cleanly to
Hibernate entities; dialects own product-specific SQL.

## Products

| `DatabaseProduct` | Dialect | Typical JDBC URL | Upsert |
|-------------------|---------|------------------|--------|
| `DB2_I` | `Db2iDialect` | `jdbc:as400://…` | `MERGE … USING (VALUES …)` |
| `H2` | `H2Dialect` | `jdbc:h2:…` | unsupported (use INSERT/UPDATE/DELETE) |
| `POSTGRES` | `PostgresDialect` | `jdbc:postgresql://…` | `INSERT … ON CONFLICT` |
| `GENERIC_JDBC` | `GenericJdbcDialect` | other `jdbc:…` | unsupported |

IBM i remains **first-class**. Local development and CI use H2 (`local` / `test` profiles).

## Core types

- `SqlDialect` — quoting, DDL types, INSERT/UPDATE/DELETE, upsert, schema helpers
- `IdentifierPolicy` — max length, case, empty/leading-digit rules
- `UpsertStrategy` / `UpsertSql` — product upsert shape + generated SQL
- `SqlDialectRegistry` — resolve dialect from `DatabaseProduct`, `ConnectionType`, or JDBC URL

## Spring wiring

- Bootstrap `DataSource` (Spring Boot) for the default connection
- Default `SqlDialect` bean: profiles `local` / `test` → H2, else DB2 i
- `SqlDialectRegistry` for product resolution
- `DbSessionFactory` opens a `DbSession` (DataSource + dialect) for either
  the bootstrap DS or a named JDBC connection profile
- Named profiles use `ConnectionPoolCache` (Hikari pools, fingerprint invalidation)
- `ImportService`, `JdbcMetadataService` and DB source connectors use sessions

Per-connection details: [connection-profiles.md](connection-profiles.md).

## Adding a dialect

1. Add a `DatabaseProduct` value if needed.
2. Implement `SqlDialect` (extend `AbstractSqlDialect` when double-quoted identifiers fit).
3. Register it in `SqlDialectRegistry` / `SqlDialectConfiguration`.
4. Extend `SqlDialectRegistry.detectProduct(jdbcUrl)`.
5. Unit-test quoting, types and upsert SQL; add H2-style integration tests where possible.
6. Never change `Db2iDialect` MERGE or 10-char create-name rules without IBM i IT (`-Pit`).

## IBM i guardrails

- Max created column name length: **10** (`IdentifierPolicy.ibmISystemNames()`)
- No schema auto-create (libraries must exist)
- DECIMAL precision/scale caps: 31
- Upsert SQL surface for DB2 i must stay stable unless explicitly versioned

## Security

Dialects never embed credentials. JDBC URLs and secrets stay in environment variables
or encrypted connection profiles under `.local/` (gitignored). Do not commit real IBM i
or production connection data.
