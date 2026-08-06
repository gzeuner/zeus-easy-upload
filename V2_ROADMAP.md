# V2 Roadmap

## tiny-tool.de Product Roadmap (2026-08-04)

Product goal: turn the current CSV-to-DB flow into a safe, comfortable mass-data
processing workspace with automatic mapping and precise manual control.

### Phase 0 — Safe foundation (current)

- Preserve the existing CSV -> DB2/400 workflow.
- Make every write operation previewable via a transaction-backed dry run.
- Expose a neutral operation model in the flow configuration.
- Keep validation, mapping and execution results explicit and testable.

### Phase 1 — Mass-data MVP

- CSV and Excel upload with encoding, delimiter and quote options.
- Insert, update, upsert/merge and delete operations.
- Automatic mapping with confidence indicators and manual overrides.
- Key-column selection, transformations and null/empty-value policies.
- Preview of affected rows, validation errors and conflict handling.

### Phase 2 — Repeatable workflows

- Save and load import profiles.
- Background jobs with progress, cancellation and retry.
- Job history, downloadable error reports and audit trail.
- API endpoints for starting and inspecting jobs.

### Phase 3 — Connectors and automation

- Database sources and targets beyond the initial DB2/400 connector.
- REST, filesystem, FTP/SFTP and object-storage connectors.
- Scheduled synchronizations and reusable multi-step flows.
- Tenant isolation, roles, secrets management and usage limits for tiny-tool.de.

### Product guardrails

- Destructive operations require an explicit preview and confirmation.
- Dry runs must use the same validation and SQL path as real executions.
- Existing successful CSV -> DB behavior remains backwards compatible.
- Large imports run in batches and never require loading the complete file into the UI.

### Current implementation slice

`Dry Run` is the first Phase 0 feature. It is available for create-table,
insert-existing, update-existing, delete-existing and upsert-existing flows
and rolls back the transaction while returning the affected-row count and
generated SQL for review.

The first Phase 1 operation slice is also implemented: existing-table flows
now expose Insert, Update, Upsert/Merge and Delete. Update and Delete require
explicit key mappings and are covered by H2 integration tests.

Import profiles are now available through `/api/profiles` as validated JSON
files, including list, load, save and delete operations.

Asynchronous imports are now available through `/api/jobs/import`, with queued,
running, succeeded, failed and cancelled states plus status listing, polling
and cancellation endpoints. The browser progress-bar can build on this API.

## Product Direction

`zeus-easy-upload` is no longer just a CSV-to-DB prototype. The current application already supports:

- Spring Boot UI flow
- CSV parsing and preview
- create-table import into DB2/400
- existing-table imports with auto-mapping
- DB metadata lookup
- MERGE-based upsert
- H2-backed integration testing with minimal SQL-dialect groundwork

The next step is not a rewrite. The next step is to introduce a connector architecture foundation so the product can evolve from a CSV import tool into a multi-connector data transfer toolkit.

Current first-class supported flow:

- CSV -> DB table

Target architecture:

- `SourceConnector -> DataFlow -> TargetConnector`

## Status Overview

### GitHub issue alignment (checked 2026-08-06)

The closed issues confirm the completed foundation: #3, #4, #8, #38 and
#42-#44 are reflected in the current connector, metadata, mapping and test
architecture. Issue #12 (CSV encoding/delimiter/quote settings) was completed
by PR #49 and is ready to be closed. Issue #10 (CSV/JSON error reports) is the
current implementation slice. The remaining open issues are tracked below by
their product package rather than treated as separate rewrites.

### Done: CSV-to-DB foundation

- `#3` Auto-Mapping auf bestehende Tabellen
- `#4` Upsert via MERGE (DB2/400)
- `#8` DB2/400 Metadata Service kapseln (DatabaseMetaData)
- `#38` Test infra: H2 in-memory integration tests + minimal SQL dialect scaffold

These items stay done. They are enabling work for the connector direction and should not be reopened.

### In Progress: Architecture evolution

The active architecture priority is to add a minimal connector seam without breaking the existing workflow.

- Analysis of current implementation and migration path
- Connector architecture foundation
- Neutral record model
- Source connector abstraction
- Target connector abstraction
- Flow execution model
- CSV source connector
- DB target connector
- Adapt current CSV -> DB execution to `DataFlow`
- Connector configuration model (`#44`)

### Planned: Multi-connector roadmap

After the minimal connector seam is in place, planned expansion areas are:

- connector configuration model
- DB source connector
- CSV target/export connector
- filesystem connector
- FTP connector foundation
- SFTP connector foundation
- REST connector foundation
- cloud/object storage connector foundation

## Track A: Connector Architecture

This track is intentionally ahead of broader platform features because it creates the seam needed for future source/target combinations.

### Epic

- `#41` Connector Architecture: Multi-connector flow foundation

### Proposed work items

- `#42` Connector architecture foundation (SourceConnector/TargetConnector) - implemented on this branch
- `#43` Neutral record model + flow execution seam - implemented on this branch
- `#44` Connector configuration model - implemented on this branch
- `#45` DB source connector
- `#46` CSV target/export connector
- `#47` Protocol connector foundations (FTP/SFTP/REST/filesystem)

### Acceptance direction

- Existing CSV -> DB behavior still works exactly as before
- Current implementation uses connector orchestration internally
- New source/target combinations can be added without reworking the controller flow again
- Existing services remain reusable implementation backbones rather than being replaced

## Track B: Production Workflow Backlog

The existing V2 epic remains relevant, but it is no longer the only organizing track.

### Existing Epic

- `#2` V2: Produktiv-Workflow (Mapping + Upsert + Async + API + Profiles)

### Still open from the V2 backlog

- `#5` Import-Profile speichern & laden
- `#6` Async Import mit Progress-Bar (UI)
- `#7` REST API zusätzlich zur GUI
- `#9` Import Job Tracking + Progress Persistence
- `#10` Fehlerreport Export (CSV/JSON Download)
- `#11` Integrations-Test-Setup (Profile "it") + Doku IBM i
- `#12` CSV Settings Optionen (Encoding/Delimiter/Quote + UI)

### Reordering guidance

These items remain valid, but architecture work should happen before major expansion in:

- generic REST/API exposure
- reusable connector configuration
- async multi-flow execution
- export-oriented workflows

## Incremental Migration Path

### Phase 1

Introduce the connector seam with minimal additive types:

- `DataRecord`
- `SourceConnector`
- `TargetConnector`
- `DataFlow`

### Phase 2

Wrap existing implementations instead of moving them:

- `CsvParsingService` stays the CSV ingestion backbone
- `ImportService` stays the DB write backbone
- `MappingService` and `MetadataService` stay unchanged

### Phase 3

Refactor only orchestration so the current flow becomes:

- CSV upload and preview
- mapping/validation
- `CsvSourceConnector`
- `DataFlow`
- `DbTableTargetConnector`

### Phase 4

Add additional connectors incrementally after the seam is proven:

- DB source
- CSV export
- filesystem
- FTP/SFTP
- REST

## Notes

- No plugin system is planned in this phase
- No generic runtime connector registry is planned in this phase
- The current UI and current import behavior must remain intact
- Existing logic should be wrapped first and only extracted further when a second real connector pair justifies it
