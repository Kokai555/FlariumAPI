# FlariumAPI Audit

## C39 — Configuration-controlled MySQL JDBC URL injection (DatabaseType)

Status: FIXED

### Root cause
`DatabaseType.MYSQL.configure` (`data/sql/DatabaseType.java:18`) interpolated the
configuration-controlled `databaseName` raw into the JDBC URL path:

```java
config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s",
        dbConfig.address(), dbConfig.port(), dbConfig.databaseName()));
```

`DatabaseConfig.load` passed `settings.getString("database", "flarium")` through
with no validation, so URL query delimiters in `databaseName` became JDBC
properties. Connector/J (mysql-connector-j 9.0.0) evidence, string-level plus
real `ConnectionUrl` parsing, no live connection:
- `mydb?useSSL=false` → database `mydb`, properties `{useSSL=false}`
- `mydb?useSSL=false&allowPublicKeyRetrieval=true` → database `mydb`, properties
  `{useSSL=false, allowPublicKeyRetrieval=true}` (security-sensitive MITM combo)
- `a/b?useSSL=false` → path escape plus properties
- `a#frag` → database silently truncated to `a`
- `a%26b` → percent-decoded to database `a&b` (encoded bypass, hence `%` rejected)

### Validation behavior
Fail-fast `IllegalArgumentException` at the configuration boundary via a
`DatabaseConfig` record compact constructor (covers `load` and direct
construction). Applies only when `type == MYSQL`; SQLITE null-field behavior
unchanged. Rejects: null/blank, length > 64 (MySQL identifier limit), and any
of `? & = # ; / \ %`, whitespace, or ISO controls. Nothing is silently
stripped or re-encoded. Legitimate names (`flarium`, `admin`, `My_DB123`,
`my-db.v2`) remain accepted with byte-identical URLs.

### Probe evidence
- Pre-fix (`C39Probe`, real `ConnectionUrl` parser): normal names parse clean;
  `?`/`&` payloads yield attacker-controlled properties (see above).
- Post-fix (`C39FixProbe`): 17/17 — 4 legitimate names accepted with empty
  parsed properties, 12 malicious/blank/control cases rejected, SQLITE nulls
  still accepted.

### Build
- `./gradlew compileJava --rerun-tasks` → BUILD SUCCESSFUL
- `./gradlew test` → BUILD SUCCESSFUL (no test sources)
- `./gradlew check` → BUILD SUCCESSFUL
- `git diff --check` → clean
- C31 `DatabaseManager` lifecycle handling untouched and preserved.

### Jev before/after
Identical C39 framing before and after (source + parser probes authoritative):
- before: is_real=true, severity=high, impact=config-controlled JDBC property
  injection (TLS/auth downgrade, e.g. `useSSL=false` + `allowPublicKeyRetrieval=true`;
  silent database truncation via `#`), human_review=confirmed via Connector/J
  parsing, decision_id=C39-20260920-01, provider/model=opencode/muse-spark-1.3-contributor-free
- after: is_real=true, severity=high (fixed), impact=eliminated — delimiter
  payloads rejected at config load; legitimate URLs unchanged (17/17 probe),
  human_review=confirmed fixed, decision_id=C39-20260920-01,
  provider/model=opencode/muse-spark-1.3-contributor-free
