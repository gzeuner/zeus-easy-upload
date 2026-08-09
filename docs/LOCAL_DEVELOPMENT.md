# Local development (H2, no IBM i)

When no IBM i system is available, develop and test against an embedded **H2**
database in DB2 compatibility mode. CREATE TABLE, INSERT, UPDATE, DELETE and
SELECT are fully exercised by the default test suite and by the `local` profile.

## Security: credentials must never reach GitHub

| What | Where | Committed? |
|------|--------|------------|
| IBM i JDBC URL / user / password | Environment variables only | **No** |
| Connection master key | `ZEUS_CONNECTION_MASTER_KEY` (env) | **No** |
| Encrypted connection profiles | `.local/connections/` or custom dir | **No** (`.local/` is gitignored) |
| H2 database files | `.local/h2/` | **No** |
| Local override config | `application-local-override.yaml` | **No** (gitignored) |
| `.env` files | project root | **No** |

Use environment variables or a secret manager. Do not paste production credentials
into YAML, Java sources, commit messages, issues, or CI logs.

Safe pattern for IBM i (only when a system is available):

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:as400://host/LIBRARY;translate binary=true"
$env:SPRING_DATASOURCE_USERNAME = "..."
$env:SPRING_DATASOURCE_PASSWORD = "..."
$env:ZEUS_CONNECTION_MASTER_KEY = "..."   # 32-byte key, Base64-encoded
# Do NOT commit these values.
```

## Run the app against H2

```bash
mvn -Plocal spring-boot:run
```

Equivalent:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

- Application: http://localhost:8080  
- H2 console: http://localhost:8080/h2-console  
  - JDBC URL: `jdbc:h2:file:./.local/h2/zeus;MODE=DB2;AUTO_SERVER=TRUE;DATABASE_TO_UPPER=true`  
  - User: `sa`  
  - Password: *(empty)*  
- Default library/schema: `TESTLIB`

Data and connection profiles are stored under `.local/` and stay out of git.

## Run tests (always H2)

```bash
mvn test
```

Default unit/integration tests use the `test` Spring profile and an in-memory H2
database. No IBM i connection is required. Browser E2E tests (`*E2ETest`) are
**excluded** from the default run so CI stays fast.

Coverage includes:

- CREATE TABLE + batch INSERT (`ImportService`, CSV import)
- INSERT into existing table
- UPDATE by key columns
- DELETE by key columns
- SELECT via `DbTableSourceConnector`
- JDBC metadata (list tables / columns)

IBM i live tests remain opt-in; see [IBM_I_INTEGRATION.md](IBM_I_INTEGRATION.md).

## Browser E2E (Playwright)

Full UI flows (CSV upload → preview → create/existing import → result, connections
page including **Verbindung testen** for JDBC H2 and REST self-check) run with
[Playwright for Java](https://playwright.dev/java/) against an embedded Spring Boot
server (profile `test` / in-memory H2).

**Why Playwright?** The app is multipage Thymeleaf with file upload, radios,
checkboxes, and client-side table loading — real browser coverage catches
session/form wiring that MockMvc does not. Playwright is a better fit here than
Selenium (less flaky, auto waits, first-class file upload) and stays in the
Maven/JUnit world (no separate Node e2e project required).

```bash
# First time only: download Chromium (~150 MB)
mvn -DskipTests exec:java -Dexec.classpathScope=test \
  -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"

# Run only *E2ETest
mvn -Pe2e test
```

Optional:

| Env | Effect |
|-----|--------|
| `E2E_HEADLESS=false` | Show the browser window |
| `E2E_SLOW_MO=250` | Slow actions (ms) for debugging |

Test sources: `src/test/java/com/zeus/upload/e2e/`.

## Profiles at a glance

| Spring / Maven profile | Database | Dialect | Typical use |
|------------------------|----------|---------|-------------|
| *(default)* | IBM i via env JDBC settings | `Db2iDialect` | Real system |
| `local` | File H2 under `.local/h2` | `H2Dialect` | Offline development |
| `test` | In-memory H2 | `H2Dialect` | `mvn test` |
| Maven `-Pe2e` | In-memory H2 + Chromium | `H2Dialect` | Browser E2E (`*E2ETest`) |
| `it` | Env-driven (usually IBM i) | `Db2iDialect` | Optional live IT |

## Switching back to IBM i

Unset or override the local profile and supply datasource env vars:

```powershell
# no -Plocal
$env:SPRING_DATASOURCE_URL = "jdbc:as400://..."
$env:SPRING_DATASOURCE_USERNAME = "..."
$env:SPRING_DATASOURCE_PASSWORD = "..."
mvn spring-boot:run
```

## Notes and limitations

- H2 `MODE=DB2` approximates IBM i SQL; identifier rules (quoted, uppercased) match the app’s sanitizing.
- DB2-style `MERGE ... USING (VALUES ...)` (upsert) is **not** supported on the H2 dialect; UPDATE/INSERT/DELETE paths are preferred for local work. Upsert remains IBM i (and PostgreSQL `ON CONFLICT`) oriented.
- Schema/libraries: H2 auto-creates missing schemas on CREATE TABLE; IBM i libraries must already exist.
- Multi-DB direction: expand `SqlDialect` / `SqlDialectRegistry` (not Hibernate). See [architecture/sql-dialects.md](architecture/sql-dialects.md).
- Named JDBC connection profiles (`DB2_400` / `POSTGRES`, endpoint `jdbc:…`) can
  target a separate pooled DataSource per import/metadata call. Create them under
  `/connections` with credentials encrypted; pick them on the import form.
  Example local H2 profile: same URL as `application-local.yaml` or a second mem/file URL.
  PostgreSQL: type `POSTGRES`, endpoint `jdbc:postgresql://localhost:5432/db` (driver included).
- **Verbindung testen** on `/connections` works for JDBC and REST profiles.
- CSV import modes: **create table** (type inference + CREATE/INSERT) or **existing table**
  (auto-map, type preflight on samples, typed INSERT conversion).
