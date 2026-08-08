# REST Connector Policy

This policy defines the minimum deployment and runtime rules for the REST
source and target connector. It is deliberately provider-neutral and is the
decision baseline for the first REST implementation package.

## Deployment profiles

The connector has two explicit profiles:

- `test`: HTTP is allowed for local emulators; credentials may be supplied by
  test configuration; no external network access is assumed.
- `production`: HTTPS is mandatory; certificate and hostname verification are
  mandatory; HTTP and redirects to HTTP are rejected.

The profile is selected by deployment configuration and cannot be changed by
an import profile or an API request.

## Destination allowlist and SSRF protection

Every request must match an explicit URL allowlist entry. The allowlist is
configured by an administrator and is not user-editable at runtime.

An entry contains:

- scheme (`https` in production; `http` may be enabled only in `test`)
- normalized hostname
- optional port (default 443 for HTTPS and 80 for HTTP)
- allowed path prefix

The connector must reject:

- URLs with credentials, fragments, unsupported schemes or unexpected ports
- loopback, link-local, multicast, unspecified and private/reserved IP ranges
  in production
- DNS results that resolve to a forbidden range
- redirects unless the complete redirect target is checked against the same
  allowlist and network policy

DNS resolution must happen at connection time and the resolved addresses must
be checked before connecting. The connector must not log authorization headers,
query secrets or complete request bodies.

## Secrets and authentication

Secrets are injected through a runtime `SecretProvider` boundary. They must
never be stored in import profiles, serialized job payloads, exceptions,
metrics, audit records or downloadable reports.

The first implementation supports:

- no authentication
- HTTP Bearer token
- HTTP Basic authentication

Credentials are attached only to requests matching the configured allowlist
entry. The token value is held in memory for the request lifecycle and is
redacted in all error messages. OAuth flows, client certificates and automatic
token refresh are explicitly out of scope for the first package.

## Request and response limits

The following limits are mandatory configuration with safe defaults:

| Setting | Default | Maximum |
|---|---:|---:|
| connect timeout | 5 s | 30 s |
| request timeout | 30 s | 5 min |
| maximum response bytes | 10 MiB | 100 MiB |
| maximum request bytes | 10 MiB | 100 MiB |
| maximum records per page | 1,000 | 10,000 |
| maximum pages per operation | 1,000 | 10,000 |
| maximum retries | 2 | 5 |

Limits are enforced while streaming. A response or request exceeding its
limit fails with a stable connector error and is not materialized further.

## Retry and rate limiting

Retries use exponential backoff with jitter and are limited to transport
errors, HTTP 408, 429 and 5xx responses. POST requests are retried only when
an idempotency key is configured and the target explicitly supports it.

The connector honors `Retry-After` for 429 and 503 responses, capped by the
configured request timeout. No retry occurs for authentication, authorization,
validation, or other 4xx errors.

An optional per-connector rate limit is enforced before sending a request.
Concurrency defaults to one request per connector instance.

## REST source contract

The first source adapter supports `GET` only and requires:

- an allowlisted endpoint
- an explicitly configured JSON content type
- a response containing either a JSON array or a configured records field
- optional page-number or cursor pagination, never both at once

Each JSON object becomes one `DataRecord`. Nested objects and arrays are kept
as JSON values. Pagination stops at the configured page limit, an absent next
cursor, or an empty page. A missing or malformed cursor is a failure.

The source streams records and must not load the complete response or complete
dataset into the UI.

## REST target contract

The first target adapter supports `POST` and `PUT` only. The method, endpoint,
request content type and response success range are explicit configuration.
The default success range is 200-299.

Records are sent in bounded JSON batches. The target reports the number of
accepted records and preserves the first actionable remote error. DELETE and
PATCH are out of scope for the first adapter and require a separate destructive
operation policy with preview and confirmation.

For POST, an idempotency key is mandatory when retries are enabled. Keys are
derived from the operation ID and batch number and are sent through the
configured idempotency header. PUT is considered idempotent only when the
configured resource URL identifies the complete batch replacement.

## Cancellation, audit and observability

Cancellation closes the active HTTP body, stops pagination and prevents new
retries or batches. Every operation receives a correlation ID. Logs and audit
events may include endpoint host/path, method, status, duration, byte counts,
record counts and retry count, but never secrets or full payloads.

Stable error categories are required:

- `CONFIGURATION`
- `SECURITY_POLICY`
- `AUTHENTICATION`
- `TIMEOUT`
- `RATE_LIMIT`
- `REMOTE_FAILURE`
- `PAYLOAD_LIMIT`
- `PARSE_FAILURE`
- `CANCELLED`

## Acceptance criteria for implementation

The REST package is ready for implementation when these tests exist:

1. allowlisted HTTPS requests succeed and non-allowlisted requests fail before
   network I/O;
2. private-IP, redirect and unsupported-scheme checks are enforced;
3. secrets are absent from exceptions, logs and serialized job/profile data;
4. timeout, response-size, page-count and retry limits are enforced;
5. source pagination streams records and stops deterministically;
6. target batches, success/error handling and idempotency headers are tested;
7. cancellation closes the response body and prevents further requests;
8. an in-process HTTP emulator covers the complete test profile without real
   external network access.

## Explicitly deferred

OAuth/client-certificate authentication, multipart uploads, webhooks,
GraphQL, DELETE/PATCH targets, distributed rate-limit coordination and
provider-specific pagination are separate packages.
