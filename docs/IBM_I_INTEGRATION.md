# IBM i Integration Tests and JDBC Setup

The default test suite and the `local` Spring profile use H2 and do not require
an IBM i system. See [LOCAL_DEVELOPMENT.md](LOCAL_DEVELOPMENT.md) for offline work.

Tests that need a real IBM i connection must be run explicitly with the Maven
`it` profile and the `it` Spring profile. **Never commit IBM i credentials**;
pass them only via environment variables.

## Running against IBM i

Set the connection values in the environment, then run:

```bash
set IT_DB_URL=jdbc:as400://my-ibmi/LIBRARY;translate binary=true
set IT_DB_USERNAME=USER
set IT_DB_PASSWORD=SECRET
mvn -Pit -Dspring.profiles.active=it verify
```

On PowerShell, use `$env:IT_DB_URL`, `$env:IT_DB_USERNAME` and
`$env:IT_DB_PASSWORD` instead. The JDBC driver defaults to
`com.ibm.as400.access.AS400JDBCDriver` and can be overridden with
`IT_DB_DRIVER`.

The integration profile is intentionally opt-in. It must never run against a
production library without an explicitly selected test schema and credentials.
Use a dedicated library and a test user with only the permissions required by
the test data lifecycle.

The existing H2 integration tests remain the fast, deterministic regression
suite for pull requests. IBM i tests are environment-dependent and should be
run by an operator or a protected CI environment with the required secrets.

