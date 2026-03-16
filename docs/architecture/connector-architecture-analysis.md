# Connector Architecture Analysis

## Current-State Assessment

The current implementation already contains the core building blocks of a future source-to-target platform, even though the application is still presented as a CSV-to-DB import tool.

### What already resembles source/target behavior

- `CsvParsingService` is effectively the current source-side ingestion component. It parses uploaded CSV files into `ParsedCsv`, preserving header order, raw rows, preview rows, delimiter detection, parse errors, and inferred column proposals.
- `ImportService` is effectively the current target-side execution component. It owns create-table import, existing-table insert, and MERGE-based upsert behavior.
- `MetadataService` and `MappingService` already provide target preparation concerns that are reusable for future connectors:
  - table/column discovery
  - auto-mapping
  - preflight validation
- `UploadController` already acts as a flow orchestrator. It performs source parsing, target preparation, validation, and final execution.

### Backbone services that should remain in place

The following services should remain the implementation backbone for the next iteration:

- `CsvParsingService`: keep as the proven CSV ingestion/parsing implementation
- `ImportService`: keep as the proven DB write/import implementation
- `MappingService`: keep existing-table mapping and validation logic intact
- `MetadataService`: keep metadata lookup intact
- `DdlService` and `SqlDialect`: keep SQL generation and dialect handling intact

These services already separate parsing, metadata, mapping, and SQL execution well enough that a connector seam can be added above them with low risk.

### Where the code is tightly coupled to CSV -> DB today

- `UploadController` directly orchestrates the full CSV -> DB workflow and branches between:
  - create-new-table import
  - existing-table insert
  - existing-table upsert
- `ImportService` accepts `ParsedCsv` and internally depends on row lists (`List<List<String>>`) rather than a neutral record abstraction.
- Existing mapping logic is expressed in CSV-column terms (`csvColumn`, `csvIndex`) and is therefore still tied to CSV-originated data.
- `ImportResult` and the controller flow are import-specific; there is no neutral flow result model yet.

### Lowest-risk connector insertion point

The safest place to introduce the connector abstraction is between:

- parsed source data (`ParsedCsv`)
- and execution against the DB target (`ImportService`)

That allows the application to add:

- a neutral `DataRecord`
- `SourceConnector`
- `TargetConnector`
- a minimal `DataFlow`

without rewriting parsing, validation, metadata, SQL generation, or DB execution.

### Wrap vs. move

For this iteration, existing logic should be wrapped, not moved.

Recommended approach:

- Wrap `ParsedCsv` with a CSV source connector
- Wrap `ImportService` with a DB table target connector
- Keep validation and preview preparation in the current controller/service flow
- Defer deeper extraction from `ImportService` until there is a second real source or target implementation

This keeps the migration additive and preserves current behavior.

## Recommended Incremental Migration Path

### Step 1: Add a minimal neutral flow seam

Introduce:

- `DataRecord`
- `SourceConnector`
- `TargetConnector`
- `DataFlow`

Keep them intentionally small and framework-neutral.

### Step 2: Wrap current CSV parsing output

Implement a CSV source connector that converts existing `ParsedCsv` rows into ordered `DataRecord` instances.

Important detail:

- column order should be preserved via insertion order in the record map
- duplicate CSV headers should not force a redesign now; the connector should favor stable ordered output over deep normalization changes

### Step 3: Wrap current DB import execution

Implement a DB table target connector that:

- accepts `Stream<DataRecord>`
- reconstructs the row-oriented structure needed by `ImportService`
- delegates to the existing create/import/upsert methods

This preserves all current SQL, batching, rollback, and error behavior.

### Step 4: Refactor orchestration only

Adapt the current CSV -> DB execution path so the controller uses:

- `CsvSourceConnector`
- `DbTableTargetConnector`
- `DataFlow`

Keep these parts unchanged:

- UI
- request/preview flow
- mapping validation
- metadata lookup
- existing import SQL behavior

### Step 5: Defer broader connector evolution

After the seam is proven, follow with:

- neutral flow configuration
- DB source connector
- CSV target/export connector
- filesystem connector
- FTP/SFTP connector foundation
- REST connector foundation

At that point deeper extraction from `ImportService` becomes justified.

## Roadmap Impact

### Existing roadmap items affected

Closed work that now becomes foundation for the connector direction:

- `#3` Auto-mapping on existing tables
- `#4` Upsert via MERGE
- `#8` Metadata service extraction
- `#38` H2 integration tests and minimal SQL dialect groundwork

These should remain marked as done and be referenced as enabling work, not reopened.

Open work that remains valid but should be clarified by the new direction:

- `#5` Import profiles
- `#6` Async import
- `#7` REST API
- `#9` Job tracking
- `#10` Error export
- `#11` IBM i integration-test profile
- `#12` CSV settings

### Recommended roadmap changes

The roadmap should be both extended and reordered.

- Extend it with an explicit connector architecture track
- Reorder it so connector foundation work happens before broader API/profile/async expansion
- Clarify that the current product is still CSV -> DB first, but is now evolving toward multi-connector flows
- Split new architecture work into dedicated issues instead of burying it inside the existing V2 epic

### Recommended new roadmap track

Add an architecture track covering:

- connector architecture foundation
- neutral record model
- source connector abstraction
- target connector abstraction
- flow execution model
- connector configuration model
- DB source connector
- CSV target/export connector
- protocol connector foundations (FTP, SFTP, REST, filesystem)

### Recommendation on issues

Create a new connector-architecture epic plus focused follow-up issues.

Reason:

- the current V2 epic is centered on the production CSV import workflow
- the connector direction is broader than that scope
- a separate epic makes dependencies and future expansion clearer without discarding the existing roadmap
