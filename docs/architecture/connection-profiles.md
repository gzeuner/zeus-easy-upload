# Connection Profiles and GUI Configuration

The GUI now provides `/connections` for managing provider-neutral connection
profiles. A profile currently contains a safe name, type (`DB2_400` or `REST`),
endpoint URL, description and optional credentials.

## Secret handling

Passwords and tokens are never stored as profile fields. They are serialized
to JSON, encrypted with AES-256-GCM and stored separately inside the profile
file as IV plus ciphertext. The profile API returns only
`credentialsConfigured: true/false`; it never returns the credentials.

The encryption key must be supplied outside the repository.

For PowerShell, generate a key with the compatible API below:

    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    [Convert]::ToBase64String($bytes)

The key can also be provided as `app.connection-master-key`. Environment
configuration is preferred for deployments. If no key is configured, the
application can still start and list non-secret metadata, but saving a
connection profile is rejected. The key is not generated automatically and
must be backed up and rotated through an explicit migration process.

## API

- `GET /api/connections` — list metadata without secrets
- `GET /api/connections/{name}` — load metadata without secrets
- `POST /api/connections` — validate and save a profile
- `DELETE /api/connections/{name}` — delete a profile

An empty credential field in the GUI preserves an existing encrypted secret.
Endpoint URLs must not contain embedded credentials or URI fragments. JDBC
URLs may contain IBM-i driver attributes such as translate binary=true; the
attribute is preserved for the driver while spaces are encoded only during
URI validation. REST endpoints must use HTTP(S); production allowlist and
SSRF checks remain part of the later runtime connector selection.

The default profile directory is connections. Local development should use
APP_CONNECTION_PROFILE_DIRECTORY=.local/connections; .local/ is ignored by
Git and must never be committed.

## Extension seam

The profile model intentionally does not contain provider-specific runtime
objects. Future connector factories can resolve a profile through
`ConnectionProfileService.loadCredentials(name)` at execution time, inject
the values into the existing REST/DB connector settings, and keep them out of
import profiles, job payloads, logs and API responses.
