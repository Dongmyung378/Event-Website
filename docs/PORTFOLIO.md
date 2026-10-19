[English](PORTFOLIO.md) | [한국어](PORTFOLIO.ko.md)

# EventLite — Project Process and Individual Contribution

EventLite manages event and venue records, schedules, public queries, and administrator changes. It combines a team application with later engineering work on query cost, data consistency, failure handling, and reproducible verification. [Run instructions](../README.md) and [architecture details](ARCHITECTURE.md) accompany the source.

[GitHub repository](https://github.com/Dongmyung378/Event-Website) | [Portfolio project](https://park-dongmyung.com/projects/project-10/)

## Timeline and attribution

| Stage | Period or evidence | Scope |
| --- | --- | --- |
| Original team project | February to May 2025 | COMP23412 Software Engineering project at the University of Manchester, built on supplied EventLite starter code |
| Earlier maintenance | Verified 2026-10-05; comparison revision `a18ca3d` | Authorization and input fixes, integration configuration, map regressions, and initial portfolio documentation |
| Subsequent strengthening | Verified in October 2026 | Database-bounded queries, consistent schedules, concurrent edits, bounded feed cache, recovery tooling, and expanded verification |

The historical [verification.json](verification.json) remains unchanged: Java 212, JavaScript 10, line coverage 95.24%, and branch coverage 72.68%. The [2026-10-15 verification](reports/verification.json) and [2026-10-19 local regression results](reports/ci-repair-verification.json) are stored separately. Later measurements are not attributed to the 2025 assignment.

**Dongmyung Park contributed test cases, security implementation, and core management functionality during the team project.** This included event and venue management and their verification. The initial map integration was handled by another team member. The complete service is the team's work; later maintenance does not redefine every source file or later test as an individual's original contribution.

The supplied course code is credited to Robert Haines, Markel Vigo, Mustafa Mustafa, Tom Carroll, and Caroline Jay. Original copyright and BSD terms are retained in [LICENSE](../LICENSE).

## Problems and engineering decisions

The application already supported normal CRUD flows. Strengthening focused on what happens when data grows, two administrators edit the same record, an external feed fails, or the application must restart from a recovered database. The original fields, paths, security policy, map functionality, and default read-only API responses were retained.

| Area | Initial condition | Change and evidence |
| --- | --- | --- |
| Schedule consistency | HTML and API next-event code independently sorted and filtered records | Shared query service, injectable clock, deterministic ID tie-breaks, and date/time-zone boundary tests |
| Query cost | Next-three and popularity calculations could load complete datasets | DB filtering, limiting, relationship aggregation, optional pagination, and measured SQL/entity counts |
| Concurrent changes | A form could overwrite data changed after it was opened | Original version fields, transactional checks, JPA optimistic locking, 409 feedback, and actual concurrent-update tests |
| Relationship changes | Venue deletion could race with event insertion | Both operations lock the same venue row; reference checks and the foreign key are retained |
| External availability | Repeated page visits could repeat a slow feed request | Fresh cache, bounded stale results, one refresh, failure backoff, and controlled delayed-transport measurement |
| Execution and recovery | A build result alone did not demonstrate a working recovery flow | Browser-driven CRUD and conflict checks, file DB restart, graceful shutdown, offline snapshots, restored record comparison, and two-OS CI configuration |

Actual browser verification also exposed a method-filter ordering issue: a valid administrator venue-deletion form could be rejected before its hidden `delete` method was applied. The filter now runs before security. Real servlet-container tests cover administrator forms and ensure that anonymous access, ordinary users, missing CSRF tokens, API writes, and unsupported methods remain blocked.

## Quantitative results

| Verification | Local Windows result, 2026-10-19 |
| --- | --- |
| Windows Java tests | 278 passed; 0 failures, errors, or skipped tests |
| JavaScript tests | 15 passed |
| Java line coverage | 95.31% (691 / 725) |
| Java branch coverage | 76.32% (232 / 304) |
| Maven coverage gates | 90% line and 70% branch thresholds passed |
| Browser verification, including initial DB upgrade | 22 passed; 0 failures |

Two new regressions verify H2 database isolation between Spring contexts: opening another context preserves existing rows, and closing it preserves the other context's schema and rows. The `test` profile now assigns each context its own memory database.

The [2026-10-19 local record](reports/ci-repair-verification.json) contains the JDK 17 results above, including the browser run with a retained legacy JAR. The [2026-10-15 record](reports/verification.json) preserves the earlier Windows/Linux 276-test results. GitHub Actions runs the 21 common browser checks with a fresh database; the optional `--seed-jar` argument adds the legacy database upgrade check.

For the fixture with 100,000 events and 100 venues:

| Operation | Initial SQL | Current SQL | Initial loaded entities | Current loaded entities |
| --- | ---: | ---: | ---: | ---: |
| Next three events | 101 | 1 | 100,100 | 6 |
| Three popular venues | 101 | 1 | 100,100 | 3 |
| Existing full-event list | 101 | 101 | 100,100 | 100,100 |

Across 1,000, 10,000, and 100,000 events, four operations produced 12 comparison conditions. All result SHA-256 values matched. The records include cases with no timing improvement, including slightly slower small-data venue-next-three measurements. Database-bounded queries were retained for their reduced work and stable query scope, rather than claiming that every small request became faster.

[Comparison](reports/query-comparison.json), [initial query measurements](reports/initial-query-benchmark.json), and [current query measurements](reports/final-query-benchmark.json) contain the raw samples and result signatures. The harness uses H2 in memory, 100 venues, five warmups, 21 samples per condition, and a fresh read transaction/persistence context for each sample. Median and p95 describe sequential service queries, not HTTP traffic or production latency. The comparison spans application and runtime revisions, so timing changes are not an isolated measurement of a single code edit.

The feed benchmark invokes the real `MastodonService` with a deterministic delayed transport and no external network. It distinguishes cold and repeated reads and records actual transport calls. Its injected delay is a test condition, not an observed Mastodon service latency.

The full-list path retains its original query structure. At 100,000 events its median was 204.146 → 203.760 ms and p95 was 231.944 → 237.591 ms. Some conditions, including 10,000 events, were slower; this compatibility path is not presented as optimized.

## Reproduce the comparison

Run commands from the current repository root. [The README](../README.md#reproduce-the-checks) covers the ordinary verification path. The benchmark scripts can measure a separate checkout while retaining the same fixture and harness. A checkout of initial revision `a18ca3d` is needed to rerun the initial side; a source ZIP can run the current side and inspect the supplied historical reports.

Windows PowerShell, assuming the initial checkout is at `../eventlite-initial`:

```powershell
$env:EVENTLITE_BENCHMARK_PROJECT = (Resolve-Path ../eventlite-initial).Path
node scripts/query-benchmark.mjs target/benchmark/initial-query.json initial 1000,10000,100000
node scripts/timeline-benchmark.mjs target/benchmark/initial-timeline.json initial 150
Remove-Item Env:EVENTLITE_BENCHMARK_PROJECT
node scripts/query-benchmark.mjs target/benchmark/current-query.json current 1000,10000,100000
node scripts/timeline-benchmark.mjs target/benchmark/current-timeline.json current 150
```

macOS / Linux:

```bash
EVENTLITE_BENCHMARK_PROJECT=../eventlite-initial node scripts/query-benchmark.mjs target/benchmark/initial-query.json initial 1000,10000,100000
EVENTLITE_BENCHMARK_PROJECT=../eventlite-initial node scripts/timeline-benchmark.mjs target/benchmark/initial-timeline.json initial 150
node scripts/query-benchmark.mjs target/benchmark/current-query.json current 1000,10000,100000
node scripts/timeline-benchmark.mjs target/benchmark/current-timeline.json current 150
```

Use the same machine and an otherwise idle session, and retain all samples. Run query measurement first because it prepares the target checkout's runtime classpath. Generated fixtures use the next day relative to execution, so reproducing an archived absolute-date SHA requires matching that fixture date; comparing both revisions on the same day checks equivalent outputs for the newly generated fixture.

To verify upgrade and recovery from an actual initial-version file database, first build its executable JAR and then run:

```text
node scripts/verify-browser.mjs --seed-jar <initial-version-executable.jar> --output target/browser-verification
```

The verifier creates isolated file databases. It checks that the initial logical records survive upgrade, that newly added null version counters become zero, and that migrated event and venue records remain editable. It also verifies restart, checksum-checked offline backup, restore to a new database, equivalent records after startup, login, and deletion. It does not copy or post to a real external account.

## Scope and retrospective

The project provides concrete evidence of backend service development: controlled API contracts, database query analysis, transaction boundaries, concurrency failure handling, automated browser verification, and recoverable local data. Keeping failed or neutral measurements makes the engineering decisions reviewable.

Coverage records executed code; it does not establish complete correctness. Local and independent verification results are distinct from GitHub Actions executions. Real Mapbox/Mastodon account responses, production traffic, and production database recovery are outside the controlled test environment. The Boot 4.0.8 runtime still uses the deprecated Jackson 2 bridge to preserve JSON compatibility; completing Jackson 3 migration is a separate maintenance task.

Original code and historical results are preserved. Source distributions exclude personal logs, database files, credentials, and Git metadata. This keeps the original team contribution, later maintenance, and reproducible public evidence understandable without exposing private working records.
