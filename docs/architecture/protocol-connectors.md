# Protocol Connector Foundations

This document defines the next connector track without committing the product
to a specific FTP, SFTP, REST, or filesystem implementation prematurely.

## Shared connector contract

Every protocol connector must fit the existing seam:

`SourceConnector -> DataFlow -> TargetConnector`

Connector-specific configuration belongs in immutable configuration objects.
Credentials and tokens must be supplied by a secret-aware runtime boundary and
must never be serialized into import profiles or logged. A connector must
report stable, actionable failures without exposing secrets.

Shared concerns for all protocol connectors:

- connection and read/write timeouts
- bounded retries with exponential backoff
- maximum payload and record sizes
- cancellation and resource cleanup
- explicit character encoding and content type
- idempotency and duplicate handling
- safe logging and correlation IDs
- metrics for records, bytes, duration, retries, and failures
- dry-run behavior where the target protocol supports a preview

## Sequencing

1. Filesystem source/target: local, deterministic, and useful for staging.
2. REST source/target: explicit HTTP method, pagination, rate-limit, and
   idempotency policies.
3. SFTP source/target: host-key verification, key-based authentication, and
   atomic temporary-file handling.
4. FTP source/target: passive mode, TLS policy, certificate validation, and
   explicit limitations for non-secure FTP.
5. Object storage: provider-neutral URI, multipart transfer, retention, and
   server-side encryption policy.

Each phase should first add configuration and contract tests, then one narrow
adapter with an emulated endpoint. Real external systems belong in protected,
opt-in integration environments.

## Safety boundaries

- Filesystem paths must be resolved against an explicitly configured root and
  rejected when they escape it.
- Remote URLs must use an allowlist and must reject unexpected schemes or
  private-network targets unless explicitly enabled by deployment policy.
- Destructive target actions require preview/confirmation and must support an
  idempotency key where the protocol permits it.
- Secrets must be injected at runtime and redacted from exceptions, logs, job
  history, and downloadable reports.
- Streaming and bounded batches are required for large transfers; connectors
  must not materialize unbounded remote data in the UI.

## Current status

The neutral connector seam, CSV target, and database source are implemented.
This document completes the architecture foundation for the protocol track.
Concrete adapters should be delivered as separate, narrowly scoped packages
once deployment policy and credential handling are available.

