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

---

## C49 — large currency amounts becoming scientific notation

**Current status: FIXED — verified against current checkout (2026-09-20).**

- **Root cause:** `CurrencyManager.give/take` built the external economy command with
  `String.valueOf(amount)` (`Double.toString`) on the `allowDecimals` path, and
  `Currency.formatDisplay` did the same. `Double.toString` switches to scientific
  notation at |x| >= 1e7, so `10000000.0` became `"1.0E7"`, `100000000.0` became
  `"1.0E8"`, and `12345678.5` became `"1.23456785E7"` inside `eco give/take` commands.
- **Fix:** new `CurrencyManager.formatAmount(Currency, double)` renders the decimals
  path via `BigDecimal.valueOf(amount).toPlainString()` (exact value, no exponent,
  no rounding); the integer path keeps byte-identical output via `Long.toString`.
  `give`/`take`/`formatDisplay` all route through it. External command syntax
  unchanged; no API-wide BigDecimal migration.
- **Probe evidence:** pre-fix probe reproduced `"1.0E7"`, `"1.0E8"`, `"1.23456785E7"`,
  `"2.500000075E7"`; post-fix probe against the real `formatAmount` passes all
  checks (large + fractional + small amounts plain and exact, integer path
  byte-identical, `eco take Steve 10000000` command shape preserved, zero strings
  containing `E`/`e`).
- **Build/test evidence:** `./gradlew compileJava --rerun-tasks` BUILD SUCCESSFUL
  (26/26 executed).
- **Jev before/after:** before `is_real=true, severity=medium,
  impact=decimals-path amounts >= 1e7 malformed in economy commands,
  human_review=high, decision_id=flariumapi-c49-2026-09-20,
  provider/model=opencode/muse-spark-1.3-contributor-free`; after `is_real=true
  (was real, now fixed), severity=medium, fix sufficient=yes, impact=none
  remaining, human_review=high, decision_id=flariumapi-c49-2026-09-20,
  provider/model=opencode/muse-spark-1.3-contributor-free`. Source + probes
  authoritative; no API keys involved.
- **C50 note:** C50 was pre-existing and preserved; this task did not regress it
  (finite/positive checks and unknown/disabled-currency throws untouched and
  re-verified, see C51 Part C).

## C51 — non-atomic hasEnough -> take check-then-act overdraft race

**Current status: FIXED — verified against current checkout (2026-09-20).**

- **Root cause:** `hasEnough` (unsynchronised balance read) and `take` (no balance
  re-check, no lock) were separate operations, so two concurrent purchase attempts
  against funds covering only one could both pass `hasEnough` and both dispatch
  `take`, overdrawing the account.
- **Fix:** bounded per-key stripe locks (64 fixed stripes keyed by
  `player UUID + normalized currency id`; no per-player allocation/leak, different
  players stay concurrent, short critical sections, no scheduler/dispatch changes
  so C52 untouched) plus a new atomic `takeIfEnough(player, currencyId, amount)`
  primitive that re-reads the balance INSIDE the per-key lock and dispatches the
  take command only when funds suffice (returns boolean). `take` keeps its exact
  fire-and-forget semantics/signature but now serialises dispatches per key.
  No public signatures changed or removed.
- **Probe evidence:** pre-fix deterministic barrier probe (balance 100, 2x take 80,
  both `hasEnough` forced to pass first) ended at `-60.0` (double-spend reproduced).
  Post-fix probe with the fixed lock+re-check shape: exactly one of the two racers
  succeeds, final balance `20.0`, never negative; different-key racers both succeed
  concurrently (no unnecessary global blocking).
- **Build/test evidence:** `./gradlew compileJava --rerun-tasks` BUILD SUCCESSFUL
  (26/26 executed).
- **Jev before/after:** before `is_real=true, severity=high,
  impact=concurrent purchases can double-spend/overdraw, human_review=high,
  decision_id=flariumapi-c51-2026-09-20,
  provider/model=opencode/muse-spark-1.3-contributor-free`; after `is_real=true
  (was real, now fixed), severity=high, fix sufficient=yes, impact=none remaining,
  human_review=high, decision_id=flariumapi-c51-2026-09-20,
  provider/model=opencode/muse-spark-1.3-contributor-free`. Source + probes
  authoritative; no API keys involved.
- **C50 note:** C50 was pre-existing and preserved; this task did not regress it
  (unknown/disabled currency and NaN/negative/zero/Infinity validation verified
  headless against the real `takeIfEnough`/`take`/`give`, all throwing
  `IllegalArgumentException` as before).

## C28 — profile login/shutdown lifecycle (AbstractProfileManager)

**Current status: FIXED — verified against current checkout (2026-09-20).**

- **Applicability (all sub-findings confirmed in current tree):**
  blocking `loadProfile().join()` in `onPreLogin` with no timeout; shutdown that
  awaited only a `pendingSaves` snapshot (in-flight loads and saves enqueued
  mid-drain never awaited); `pendingLoads` removal staged inside the
  `computeIfAbsent` mapping, which both opened a remove-before-cache-put window
  for duplicate DB hits and threw `ConcurrentHashMap` "Recursive update" for
  synchronously-completed database futures; eviction listener saving snapshots
  while an online copy existed (races live mutations on the eviction thread) and
  letting a synchronously-throwing implementation propagate into Caffeine internals.
- **Fix (`data/cache/AbstractProfileManager.java`, no API breakage):** pre-login
  wait bounded via `get(loginTimeoutSeconds())` (default 10s; event already fires
  on Paper's async login thread, main thread never blocked) with kick on timeout;
  `pendingLoads` holds the terminal stage with removal attached after the cache
  put and outside the mapping function (coalescing preserved, no recursive
  update, null/sync-throwing implementations contained); shutdown sets a
  `shuttingDown` flag (new loads rejected, late completions no longer
  repopulate), then drains saves AND loads in a deadline loop (default 5s total,
  saves enqueued mid-drain picked up); eviction saves skipped while an
  `activeProfiles` copy exists (quit/shutdown path owns online persistence);
  saves routed via a null/exception-safe tracking helper. `onQuit` stays
  non-blocking; no Folia-owned thread gains arbitrary blocking.
- **Probe evidence:** `ProfileManagerLifecycleTest` (7 tests, real SQLite-free
  headless manager with mocked `Bukkit`/`JavaPlugin`): concurrent 8-caller loads
  coalesce to 1 DB hit; hung login load kicks within timeout (pre-fix negative:
  handler stuck, bounded 5s wait timed out); failed load kicks and retry
  succeeds; shutdown completes cleanly with persistence+invalidation; shutdown
  bounded with hanging save and with in-flight load; eviction persists the
  evicted entry. Pre-fix run reproduced the login hang and the "Recursive
  update" failures in bounded form.
- **Build/test evidence:** `./gradlew compileJava --rerun-tasks` BUILD
  SUCCESSFUL; `./gradlew test` 13/13 pass (7 C28 + 6 C40); `./gradlew check`
  BUILD SUCCESSFUL; `git diff --check` clean.
- **Jev before/after:** identical C28 framing (source state before/after);
  before and after both `HTTP 401 (missing or invalid API key)` — no key in
  this environment (`TYPESAFE_API_KEY` placeholder), so no
  `is_real/severity/impact/human_review` scores; source + bounded probes
  authoritative. provider/model=opencode/muse-spark-1.3-contributor-free.

## C40 — SQLite transaction re-entry starvation (DatabaseType/DatabaseManager)

**Current status: FIXED — verified against current checkout (2026-09-20).**

- **Applicability (confirmed in current tree):** `DatabaseType.SQLITE` sets
  `maximumPoolSize(1)`; `executeTransaction` held that single connection while
  the callback could re-enter the manager (`executeUpdate`/`executeQuery`/
  `executeQueryList`/`executeBatch`/`executeTransaction`), which then waited for
  a second connection until Hikari's `connectionTimeout` (~30s starvation).
- **Fix (`data/sql/DatabaseManager.java`, pool size unchanged on purpose):**
  SQLite stays at pool size 1 (single-writer; enlarging would trade starvation
  for `SQLITE_BUSY` lock failures). Transaction semantics made safe and
  explicit: per-thread owner tracking fails re-entrant pool-backed calls fast
  with `IllegalStateException` (synchronous guard in the query/update/batch
  methods, failed-future guard for nested `executeTransaction`), the owner is
  cleared in the existing C31 `finally`; new additive `update`/`query`/
  `queryList(Connection, ...)` static helpers run statements on the
  transaction-owned connection; `executeTransaction` javadoc states the
  non-reentrancy contract. C31 hardening preserved verbatim (pool-before-
  executor init, setter/mapper error logging, rollback on `Throwable`, executor-
  before-pool close order); no SQL semantics altered.
- **Probe evidence:** `DatabaseTransactionTest` (6 tests, real SQLite via
  temporary data folder): re-entrant callback fails fast with
  `IllegalStateException` (pre-fix negative: bounded 8s wait timed out =
  starvation reproduced); commit persists; failure rolls back; connection-bound
  helpers work inside a transaction; 6 concurrent transactions serialize and
  all complete; pool size asserted to remain 1.
- **Build/test evidence:** same as C28 above (13/13, check clean).
- **Jev before/after:** identical C40 framing; before and after both `HTTP 401
  (missing or invalid API key)`, no scores; source + bounded probes
  authoritative. provider/model=opencode/muse-spark-1.3-contributor-free.
