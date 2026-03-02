# V2 Roadmap

`gh` is not available in this environment. Create GitHub issues manually from the definitions below.

## Epic (P1)

### Title
V2: Produktiv-Workflow (Mapping + Upsert + Async + API + Profiles)

### Labels
`epic`, `enhancement`, `import`, `priority:P1`

### Description
Goal:
- Enable production-grade CSV import workflows: mapping to existing tables, upsert, async progress, REST API, reusable profiles.

Scope (In):
- Auto-map CSV columns to existing DB2/400 table columns
- Upsert via DB2/400 MERGE based on selectable key columns
- Save/load import profiles (mapping + settings)
- Async imports with progress tracking and UI progress bar
- REST API endpoints mirroring the GUI workflow

Scope (Out):
- AuthN/AuthZ beyond minimal/basic protection
- Complex transformations (derive columns, joins)
- CSV validation rules engine (beyond type parsing + required columns)

Work items (Issues):
- [ ] Feature: Auto-Mapping auf bestehende Tabellen
- [ ] Feature: Upsert via MERGE (DB2/400)
- [ ] Feature: Import-Profile speichern & laden
- [ ] Feature: Async Import mit Progress-Bar (UI)
- [ ] Feature: REST API zusätzlich zur GUI
- [ ] Tech: DB2/400 Metadata Service kapseln (DatabaseMetaData)
- [ ] Tech: Import Job Tracking + Progress Persistence
- [ ] Tech: Fehlerreport Export (CSV/JSON Download)
- [ ] Tech: Integrations-Test-Setup (Profile "it") + Doku IBM i
- [ ] Feature/Tech: CSV Settings Optionen (Encoding/Delimiter/Quote)

Epic acceptance criteria:
- [ ] All listed work items are implemented and documented
- [ ] A full end-to-end run is possible: upload -> preview/mapping -> async import -> progress -> result/errors via UI and API
- [ ] Upsert mode updates existing keys and inserts new ones correctly
- [ ] Profiles can be saved and reused to repeat the same import setup

## Issue Backlog

### 1) Auto-Mapping auf bestehende Tabellen (Feature, P1)
Labels: `enhancement`, `db2`, `import`, `ui`, `priority:P1`
Part of: `#<EPIC>`

Problem / Value:
- Users need to import into existing DB2/400 tables without recreating them, while ensuring correct column mapping and type compatibility.

Scope (In):
- UI option: "Use existing table" (instead of "Create new table")
- List tables by library/schema using DatabaseMetaData
- Read columns (name, type, length, precision, scale, nullable)
- Mapping UI: CSV column -> DB column dropdown; allow "ignore"
- Preflight validation: required (non-null) columns must be mapped; no duplicate target mapping

Scope (Out):
- Automatic schema migrations / altering existing columns
- Advanced transformations (concats, computed columns)

Acceptance criteria:
- [ ] UI allows selecting an existing table from a library/schema
- [ ] UI shows DB columns and allows mapping each CSV column to exactly one DB column (or ignore)
- [ ] Validation prevents start if any NOT NULL DB columns are unmapped and have no default
- [ ] Import uses mapping to insert/update correct target columns
- [ ] A sample import into an existing table completes with correct values

Notes:
- Implement metadata access via a dedicated MetadataService.

### 2) Upsert via MERGE (DB2/400) (Feature, P1)
Labels: `enhancement`, `db2`, `import`, `api`, `ui`, `priority:P1`
Part of: `#<EPIC>`

Problem / Value:
- Insert-only is not enough for recurring imports. Need UPSERT based on key columns.

Scope (In):
- UI/REST option: Mode INSERT_ONLY vs UPSERT
- User selects one or more key columns (DB columns) for match condition
- Implement MERGE INTO <LIB>.<TABLE> ... ON (keys) WHEN MATCHED THEN UPDATE ... WHEN NOT MATCHED THEN INSERT ...
- Batch approach or per-row MERGE (choose pragmatic for v1): correctness first

Scope (Out):
- Complex conflict resolution policies
- Partial updates based on changed fields only (optional later)

Acceptance criteria:
- [ ] In UPSERT mode, rows with existing keys are updated, new keys inserted
- [ ] Keys selection supports 1..n columns
- [ ] Transaction rollback on failure, with clear error message including row/column
- [ ] Works via both GUI flow and REST API
- [ ] Document DB2/400 MERGE limitations/assumptions in README

### 3) Import-Profile speichern & laden (Feature, P2)
Labels: `enhancement`, `import`, `ui`, `priority:P2`
Part of: `#<EPIC>`

Problem / Value:
- Repeated imports require redoing mapping/settings each time; profiles make workflows repeatable.

Scope (In):
- Profile contains: targetLibrary, tableName, mode (insert/upsert), key columns, CSV settings (delimiter/quote/encoding), mapping (csv->db), type overrides (when create mode)
- UI: Save profile, Load profile, Delete profile
- Storage: start with DB table (preferred) OR JSON files under a configurable folder

Scope (Out):
- Multi-user permission model
- Versioned profile migrations (beyond minimal)

Acceptance criteria:
- [ ] User can save a profile with a name
- [ ] User can load the profile and UI rehydrates mapping + settings
- [ ] User can delete a profile
- [ ] Profile persistence is documented (where stored, how to backup)

### 4) Async Import mit Progress-Bar (UI) (Feature, P2)
Labels: `enhancement`, `ui`, `import`, `priority:P2`
Part of: `#<EPIC>`

Problem / Value:
- Large CSV imports should not block HTTP requests or time out; users need progress visibility.

Scope (In):
- Start import returns a jobId
- Background job executes import
- Progress tracking: totalRows, processedRows, inserted, updated (if upsert), failed, status (QUEUED/RUNNING/DONE/FAILED/CANCELLED)
- UI shows progress bar and live updates (polling acceptable)
- Result page shows summary and errors

Scope (Out):
- WebSockets (polling is fine for v1)
- Distributed job queue

Acceptance criteria:
- [ ] Import runs in background without request timeout
- [ ] UI displays progress and updates at least every 1-2 seconds (polling)
- [ ] On completion, UI shows final counts + errors
- [ ] Cancel is optional; if implemented, must stop the job cleanly

### 5) REST API zusätzlich zur GUI (Feature, P2)
Labels: `enhancement`, `api`, `import`, `priority:P2`
Part of: `#<EPIC>`

Problem / Value:
- Automation / integrations require a stable REST API in addition to the UI.

Scope (In):
- Endpoints (example):
  - POST /api/uploads -> upload CSV, returns uploadId + inferred schema
  - POST /api/imports -> start import, returns jobId
  - GET /api/imports/{id} -> status/progress
  - GET /api/imports/{id}/errors -> error report
- JSON models align with existing domain models
- API docs: minimal README section (OpenAPI optional)

Scope (Out):
- OAuth/JWT (optional later)
- Multi-tenant separation

Acceptance criteria:
- [ ] API can perform full flow: upload -> start import -> poll status -> read errors
- [ ] API supports insert-only and upsert mode
- [ ] Validation errors return 4xx with meaningful messages

### 6) DB2/400 Metadata Service kapseln (DatabaseMetaData) (Enabler, P1)
Labels: `tech-debt`, `db2`, `import`, `priority:P1`
Part of: `#<EPIC>`

Context:
- Multiple features depend on robust introspection: existing tables, columns, types, nullable, defaults.

Scope (In):
- Create MetadataService with methods:
  - listLibraries/schemas (if possible)
  - listTables(library)
  - getColumns(library, table) -> name/type/length/precision/scale/nullable/default
- Centralize DB2 type normalization (map JDBC types to internal representation)

Scope (Out):
- Caching layers (optional later)

Acceptance criteria:
- [ ] MetadataService returns correct tables/columns for a given library
- [ ] Results are used by mapping UI and/or validation
- [ ] Unit tests cover type normalization logic

### 7) Import Job Tracking + Progress Persistence (Enabler, P2)
Labels: `tech-debt`, `import`, `priority:P2`
Part of: `#<EPIC>`

Context:
- Async import requires reliable progress tracking, and ideally persistence across restarts.

Scope (In):
- Define ImportJob entity with status + counters + timestamps
- Provide InMemory implementation first, plus optional DB persistence toggle
- Provide JobService: createJob, updateProgress, complete, fail

Scope (Out):
- Distributed lock/queue

Acceptance criteria:
- [ ] Job progress is queryable via service and API
- [ ] UI polling reads consistent progress
- [ ] On failure, job status is FAILED with error summary

### 8) Fehlerreport Export (CSV/JSON Download) (Enabler, P2)
Labels: `enhancement`, `ui`, `api`, `import`, `priority:P2`
Part of: `#<EPIC>`

Problem / Value:
- Users need to download error details for troubleshooting and rework.

Scope (In):
- Provide downloadable error report:
  - JSON (full detail)
  - CSV (flattened: row, column, value, message)
- UI buttons on result page
- API endpoint for error export

Scope (Out):
- Pretty HTML reports

Acceptance criteria:
- [ ] After a failed import (or partial errors), user can download JSON and CSV report
- [ ] API provides the same reports
- [ ] Report includes jobId, timestamp, and error list

### 9) Integrations-Test-Setup (Profile "it") + Doku IBM i (Enabler, P3)
Labels: `tech-debt`, `db2`, `priority:P3`
Part of: `#<EPIC>`

Context:
- Critical DB2/400 behaviors (MERGE, identifier limits, data types) should be verified against a real IBM i.

Scope (In):
- Add Maven profile "it" that can run integration tests when env vars are provided:
  - IT_DB_URL, IT_DB_USER, IT_DB_PASS
- Provide one or two smoke tests:
  - create table (temp name)
  - insert
  - merge/upsert (when implemented)
- Document in README how to run and required permissions

Scope (Out):
- CI pipeline automation (optional later)

Acceptance criteria:
- [ ] `mvn -Pit test` runs integration tests when env vars exist
- [ ] Tests are skipped gracefully when env vars are missing
- [ ] README contains clear instructions

### 10) CSV Settings Optionen (Encoding/Delimiter/Quote + UI) (Feature/Enabler, P3)
Labels: `enhancement`, `ui`, `import`, `priority:P3`
Part of: `#<EPIC>`

Problem / Value:
- Real-world CSV varies widely (Excel exports). Users need control over delimiter/quote/encoding.

Scope (In):
- UI options:
  - delimiter (comma, semicolon, tab)
  - quote char (", ')
  - encoding (UTF-8 default; optional ISO-8859-1, Windows-1252)
- Optional auto-detect heuristic (nice-to-have)
- Persist in import profile

Scope (Out):
- Full RFC edge cases (multi-line quoted fields are already handled by parser)

Acceptance criteria:
- [ ] User can set delimiter/quote/encoding before parsing
- [ ] Parsing reflects these settings and preview matches expected
- [ ] Settings are saved/loaded with profiles

## Manual creation order
1. Create the Epic issue first.
2. Create issues 1-10 and set `Part of: #<EPIC_NUMBER>` in each body.
3. Add labels exactly as listed for each issue.