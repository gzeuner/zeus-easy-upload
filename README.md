# zeus-easy-upload

CSV-Import und Tabellen-CRUD für **IBM i DB2/400** — mit lokaler Entwicklung gegen **H2**, optional **PostgreSQL**, verschlüsselten Verbindungsprofilen und Browser-E2E.

[![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Maven](https://img.shields.io/badge/Maven-3.9+-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/license-see%20repo-lightgrey)](#)

### Tags / Stack

| Kategorie | Technologien |
|-----------|----------------|
| **Runtime** | Java 17, Spring Boot 3.5, Maven |
| **Web / UI** | Spring Web, Thymeleaf, Bootstrap 5 |
| **Daten** | JDBC (kein Hibernate/ORM), HikariCP, Apache Commons CSV |
| **Datenbanken** | IBM i DB2/400 (jt400), H2 (local/test), PostgreSQL (optional) |
| **SQL** | `SqlDialect`-Layer (DB2 i, H2, Postgres, Generic) |
| **Security** | AES-256-GCM für Connection-Secrets, Master-Key via Env |
| **Tests** | JUnit 5, AssertJ, Spring Boot Test, **Playwright** E2E (opt-in) |
| **Connectoren** | CSV, DB-Tabelle, Filesystem-CSV, REST JSON |

---

## Deutsch

### Überblick

**zeus-easy-upload** ist eine Spring-Boot-Webanwendung zum Import von CSV-Dateien in relationale Tabellen — primär **IBM i (DB2/400)**, offline und in CI gegen **H2**.

**Was die App heute kann**

- CSV hochladen (Delimiter/Encoding/Quote konfigurierbar)
- Spaltentypen vorschlagen (INTEGER, BIGINT, DECIMAL, DATE, TIMESTAMP, VARCHAR)
- **Neue Tabelle anlegen** (CREATE TABLE + INSERT), optional **Drop before create**
- **In vorhandene Tabelle schreiben**: INSERT, **UPDATE**, **DELETE** (Schlüsselspalten), UPSERT wo der Dialekt es unterstützt
- Typisierte Wertkonvertierung und Mapping-Preflight
- Verbindungsprofile (`/connections`): DB2/400, PostgreSQL, REST — Secrets AES-verschlüsselt
- **Verbindung testen** (JDBC + REST HTTP-Check)
- Named JDBC-Profile für Import/Metadaten (Hikari-Pool-Cache)
- Dry-Run (Transaktion wird zurückgerollt)

### Schnellstart (lokal, ohne IBM i)

```bash
mvn -Plocal spring-boot:run
```

| | URL / Hinweis |
|--|----------------|
| App | http://localhost:8080 |
| H2-Konsole | http://localhost:8080/h2-console |
| Default-Schema | `TESTLIB` |
| Daten / Profile | `.local/` (gitignored) |

Details: [docs/LOCAL_DEVELOPMENT.md](docs/LOCAL_DEVELOPMENT.md)

### Nutzung (GUI)

1. **Import** (`/`): CSV wählen, Modus *Neue Tabelle* oder *Vorhandene Tabelle*, Library/Schema, ggf. Drop/Recreate
2. **Vorschau**: Typen anpassen bzw. Mapping + Operation (INSERT / UPDATE / DELETE / UPSERT) + Schlüsselspalten
3. **Ergebnis**: Status, betroffene Zeilen, SQL, Fehlerliste (CSV/JSON-Export)

**Verbindungen** (`/connections`): Profile speichern und **Verbindung testen**. REST-Profile sind für Erreichbarkeit/Auth, nicht als CSV-Import-Ziel.

### Start gegen IBM i

Credentials **nur** über Umgebung — nie committen:

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:as400://host/LIB;translate binary=true"
$env:SPRING_DATASOURCE_USERNAME = "..."
$env:SPRING_DATASOURCE_PASSWORD = "..."
$env:ZEUS_CONNECTION_MASTER_KEY = "..."   # Base64, 32 Byte, für verschlüsselte Profile
mvn spring-boot:run
```

Vorlage: [`.env.example`](.env.example) · Live-Tests: [docs/IBM_I_INTEGRATION.md](docs/IBM_I_INTEGRATION.md)

### Architektur (kurz)

```
CSV / Filesystem / REST  →  SourceConnector  →  DataFlow  →  TargetConnector  →  DB / Datei / HTTP
```

| Paket | Rolle |
|-------|--------|
| `controller` | Web-Flow, API (Connections, Metadata, Jobs) |
| `service` | CSV, Typinferenz, Import, Mapping, Sessions, Pools, Crypto |
| `sql` | Dialekte, Identifier, INSERT/UPDATE/DELETE/MERGE |
| `connector` | CSV, DB, Filesystem, REST |
| `flow` | neutrale Flow-Konfiguration und Write-Modes |
| `domain` | Request/Result/Profile-Modelle |

- Multi-DB über **JDBC + `SqlDialect`**, nicht ORM  
- Docs: [docs/architecture/](docs/architecture/) (sql-dialects, connection-profiles, REST-Policy, …)

### Konfiguration

| Variable / Property | Bedeutung |
|---------------------|-----------|
| `SPRING_DATASOURCE_*` | Bootstrap-JDBC (IBM i oder H2 im `local`-Profil) |
| `ZEUS_CONNECTION_MASTER_KEY` / `app.connection-master-key` | AES-Key für Profile (Base64, 256 Bit) |
| `APP_CONNECTION_PROFILE_DIRECTORY` | Speicherort der Profile (Default local: `.local/connections`) |
| `app.default-library` | Default-Schema/Library |
| `app.sample-rows` / `app.batch-size` | Typinferenz-Sample / INSERT-Batch |

Profil **`local`**: `application-local.yaml` (H2 file DB).

### Tests

```bash
# Unit + Integration (H2, kein IBM i) — Default-CI
mvn test

# Browser-E2E (Playwright, opt-in)
mvn -DskipTests exec:java -Dexec.classpathScope=test \
  -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
mvn -Pe2e test
```

E2E deckt u. a. ab: Create/Drop-Recreate, Existing INSERT/UPDATE/DELETE, Connection-Test (JDBC + REST).

### Beispiel-CSV

- `src/main/resources/examples/sample.csv`
- `src/main/resources/examples/test-import.csv` (Decimals, Datumsformate)

### Sicherheit

- Keine Produktions-Credentials im Git
- Secrets nur Env / Secret-Manager
- `.local/` und Connection-Dateien sind gitignored
- API liefert nie Klartext-Passwörter zurück

### Roadmap (Auszug)

- UPSERT auf H2 (aktuell IBM-i-MERGE / Postgres ON CONFLICT; H2: INSERT/UPDATE/DELETE)
- FTP/SFTP / Object Storage
- Persistente Job-Historie, reichere Mapping-UX

---

## English

### Overview

**zeus-easy-upload** is a Spring Boot web app for importing CSV files into relational tables — primarily **IBM i (DB2/400)**, with **H2** for local/CI work.

**Current capabilities**

- Upload CSV (delimiter / encoding / quote options)
- Infer column types (INTEGER, BIGINT, DECIMAL, DATE, TIMESTAMP, VARCHAR)
- **Create table** (CREATE TABLE + INSERT), optional **drop before create**
- **Existing table**: INSERT, **UPDATE**, **DELETE** (key columns), UPSERT where the dialect supports it
- Typed value conversion and mapping preflight
- Connection profiles (`/connections`): DB2/400, PostgreSQL, REST — secrets AES-encrypted
- **Connection test** (JDBC + REST HTTP check)
- Named JDBC profiles for import/metadata (Hikari pool cache)
- Dry-run (transaction rolled back)

### Quick start (local, no IBM i)

```bash
mvn -Plocal spring-boot:run
```

| | |
|--|--|
| App | http://localhost:8080 |
| H2 console | http://localhost:8080/h2-console |
| Default schema | `TESTLIB` |
| Data / profiles | `.local/` (gitignored) |

Details: [docs/LOCAL_DEVELOPMENT.md](docs/LOCAL_DEVELOPMENT.md)

### Using the UI

1. **Import** (`/`): upload CSV, choose *create table* or *existing table*, library/schema, optional drop/recreate
2. **Preview**: adjust types or mapping + operation (INSERT / UPDATE / DELETE / UPSERT) + key columns
3. **Result**: status, rows affected, SQL, error list (CSV/JSON download)

**Connections** (`/connections`): save profiles and **test connection**. REST profiles are for reachability/auth, not CSV→table import targets.

### Run against IBM i

Use **environment variables only** — never commit credentials:

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:as400://host/LIB;translate binary=true"
$env:SPRING_DATASOURCE_USERNAME = "..."
$env:SPRING_DATASOURCE_PASSWORD = "..."
$env:ZEUS_CONNECTION_MASTER_KEY = "..."   # Base64, 32 bytes, for encrypted profiles
mvn spring-boot:run
```

Template: [`.env.example`](.env.example) · Live tests: [docs/IBM_I_INTEGRATION.md](docs/IBM_I_INTEGRATION.md)

### Architecture (short)

```
CSV / filesystem / REST  →  SourceConnector  →  DataFlow  →  TargetConnector  →  DB / file / HTTP
```

| Package | Role |
|---------|------|
| `controller` | Web flow, APIs (connections, metadata, jobs) |
| `service` | CSV, type inference, import, mapping, sessions, pools, crypto |
| `sql` | Dialects, identifiers, INSERT/UPDATE/DELETE/MERGE |
| `connector` | CSV, DB, filesystem, REST |
| `flow` | Neutral flow config and write modes |
| `domain` | Request/result/profile models |

- Multi-DB via **JDBC + `SqlDialect`**, not ORM  
- Docs: [docs/architecture/](docs/architecture/)

### Configuration

| Variable / property | Purpose |
|---------------------|---------|
| `SPRING_DATASOURCE_*` | Bootstrap JDBC (IBM i, or H2 with `local` profile) |
| `ZEUS_CONNECTION_MASTER_KEY` / `app.connection-master-key` | AES key for profiles (Base64, 256-bit) |
| `APP_CONNECTION_PROFILE_DIRECTORY` | Profile storage (local default: `.local/connections`) |
| `app.default-library` | Default schema/library |
| `app.sample-rows` / `app.batch-size` | Type-inference sample / insert batch size |

Profile **`local`**: `application-local.yaml` (file-backed H2).

### Tests

```bash
# Unit + integration (H2, no IBM i) — default CI
mvn test

# Browser E2E (Playwright, opt-in)
mvn -DskipTests exec:java -Dexec.classpathScope=test \
  -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
mvn -Pe2e test
```

E2E covers create/drop-recreate, existing INSERT/UPDATE/DELETE, and connection tests (JDBC + REST).

### Sample CSV

- `src/main/resources/examples/sample.csv`
- `src/main/resources/examples/test-import.csv` (decimals, date formats)

### Security

- No production credentials in Git
- Secrets via env / secret manager only
- `.local/` and connection files are gitignored
- APIs never return plaintext passwords

### Roadmap (excerpt)

- H2 UPSERT (today: IBM i MERGE / Postgres ON CONFLICT; H2: INSERT/UPDATE/DELETE)
- FTP/SFTP / object storage
- Persistent job history, richer mapping UX

---

## Further documentation

| Doc | Content |
|-----|---------|
| [docs/LOCAL_DEVELOPMENT.md](docs/LOCAL_DEVELOPMENT.md) | H2 local, secrets, Playwright E2E |
| [docs/IBM_I_INTEGRATION.md](docs/IBM_I_INTEGRATION.md) | Live IBM i tests |
| [docs/architecture/sql-dialects.md](docs/architecture/sql-dialects.md) | Dialect layer |
| [docs/architecture/connection-profiles.md](docs/architecture/connection-profiles.md) | Encrypted profiles, pools, REST test |
| [docs/architecture/rest-connector-policy.md](docs/architecture/rest-connector-policy.md) | REST security policy |
| [V2_ROADMAP.md](V2_ROADMAP.md) | Longer-term roadmap |

## Metadata API (read-only)

- `GET /meta/tables?library=LIB` — optional `connectionProfileName`
- `GET /meta/columns?library=LIB&table=TABLE` — optional `connectionProfileName`
- `GET|POST|DELETE /api/connections` — connection profiles
- `POST /api/connections/{name}/test` — connectivity test
