# Review map: "Allow health indicators to run with a specified timeout"

## Big picture

```
HTTP / JMX
   │
HealthEndpoint / HealthEndpointWebExtension / ReactiveHealthEndpointWebExtension
   │   (new ctor arg: executor; old ctor @Deprecated → noop PropertyResolver)
HealthEndpointSupport ── passes full path name ("db/primary") ──┐
   │                                                             │
Contributor.Blocking / Contributor.Reactive  getDescriptor(name, details)
   │                                   │
HealthIndicatorExecutor      ReactiveHealthIndicatorExecutor
   │  (blocking)                       │  HealthIndicatorAdapter? → delegate to blocking executor (POOL)
   │                                   │
   ├─ HealthIndicatorTimeouts  management.health.<name>.timeout → defaults.timeout → none
   ├─ instanceof TimeoutAware(Reactive)HealthIndicator?
   │     no  → FRAMEWORK, indicator.health(details)        (plain indicator, unchanged call)
   │     yes → getTimeoutEnforcement(), indicator.health(timeout, details)
   └─ InFlightExecutions  max 4 per (name, includeDetails), join running check until its deadline
           │
       FRAMEWORK: thread pool (SynchronousQueue, unbounded) or virtual thread per task
                  CompletableFuture.orTimeout → cancel(true)
       INDICATOR: calling thread, no framework cap
```

API shape:

```
HealthIndicator                         unchanged vs main
  └─ TimeoutAwareHealthIndicator        new: abstract health(Duration), default getTimeoutEnforcement() = FRAMEWORK
       └─ AbstractTimeoutAwareHealthIndicator   doHealthCheck(builder, @Nullable Duration)
ReactiveHealthIndicator → TimeoutAwareReactiveHealthIndicator → AbstractTimeoutAwareReactiveHealthIndicator
```

Result on failure: `Health.down(ex)` + `reason` = `timeout | concurrency-limit | invalid-timeout | rejected | disposed | execution-failed` (`DownReason`).

## Hot spots (look hardest here)

Ranked by blast radius × how hard to fix later.

### 1. Behaviour change with NO timeout configured

Default users should see zero difference. One path breaks that:

- `ReactiveHealthIndicatorExecutor.doExecute` routes every `HealthIndicatorAdapter` (blocking indicator in a WebFlux app) to `executeBlocking` → `ThreadingMode.POOL` → `inFlight.start(...)`.
- That path enforces `MAX_EXECUTIONS_PER_KEY = 4` even when timeout is `null`.
- Result: WebFlux app, slow JDBC check, >4 concurrent probes → `DOWN reason=concurrency-limit`. Previously `boundedElastic`, no cap. K8s probes could start failing pods.
- Verified: intended and tested (`ReactiveHealthIndicatorExecutorTests.shouldApplyConcurrencyLimitToAdaptedBlockingIndicatorWithoutDeadline`). Unchanged by the sub-interface refactor. Open: is the default change acceptable?
- Same pool and limit for every blocking indicator in WebFlux, whatever the enforcement:

  | Case | Runs on | Capped |
  |---|---|---|
  | no timeout | pool | no (limit only) |
  | timeout, plain | pool | yes |
  | timeout, aware, `FRAMEWORK` | pool | yes |
  | timeout, aware, `INDICATOR` | pool | no (limit only) |

- No limit on the calling thread: servlet blocking indicator without deadline, reactive indicator without timeout or `INDICATOR`.

### 2. Public API shape (frozen once released)

- `HealthIndicator` / `ReactiveHealthIndicator` are unchanged vs `main` (`016a79bcc53`). Timeout support is opt-in via `TimeoutAwareHealthIndicator` / `TimeoutAwareReactiveHealthIndicator`; detected with `instanceof`, no reflection.
- `INDICATOR` without `health(Duration)` can't compile anymore (abstract). Default enforcement on the sub-interfaces is `FRAMEWORK`.
- `TimeoutAwareReactiveHealthIndicator#asHealthContributor()` overridden with covariant return `TimeoutAwareHealthIndicator` (`4382216a0e7`). Alternative without new API: `instanceof` in the `ReactiveHealthIndicator` default.
- Still no-op `health(Duration) { return health(); }` in Ping/SSL/Availability/Cassandra/Couchbase: needed to declare `INDICATOR` (stay on calling thread). Acceptable?
- `TimeoutAwareHealthIndicator` is not a `@FunctionalInterface` (two abstract methods). OK?
- New public types: 2 interfaces, `TimeoutEnforcement`, `HealthIndicatorTimeouts` (+ public exception), both executors (`@ConditionalOnMissingBean` → replaceable), `AbstractTimeoutAware*` base classes with `final` methods.
- Question: does this need to be public, or can it be smaller?

### 3. Slot accounting (`InFlightExecutions`, `AsyncCheck`)

A leaked slot = indicator permanently `DOWN` until restart.

- Every `tryAcquire` needs exactly one `release` on: normal end, check throws, timeout before task starts, timeout while running, `RejectedExecutionException`, `starter.get()` throws, `shutdownNow` during `destroy()`.
- Side effects inside `ConcurrentHashMap.compute` (`tryAcquire` → nested `compute` on another map, `starter.get()`).
- `SharedCheck` (reactive): `doFinally` always fires? Hot upstream when all subscribers cancel?
- Draw the state machine; walk each path.

### 4. Thread hop for checks

`FRAMEWORK` checks with a timeout now run on `health-check-*` threads, not the request thread.

- ThreadLocals lost: security context, MDC, tracing/observation, transaction/request context, TCCL.
- Hits user indicators too (plain indicators are always `FRAMEWORK`).
- `management.health.defaults.timeout` moves *every* `FRAMEWORK` indicator off-thread.

### 5. Removals / breaking

- JMS: `start-timeout` property, `(ConnectionFactory, Duration)` ctor, `DEFAULT_START_TIMEOUT` gone. Confirm 4.2.0 unreleased.
- `@author` removed in `JmsHealthContributorAutoConfiguration`.
- private → package-private in `AbstractHealthIndicator` / `AbstractReactiveHealthIndicator`.

### 6. Per-indicator semantics

- Rounding must never shorten: `DataSource` (seconds), `Mongo` (ms), `Neo4j` (ms).
- `timeout == null` path unchanged.
- Hazelcast transaction lifespan only checked on commit: effectively no enforcement.

### Skim

Metadata JSON, docs samples, endpoint ctor plumbing, `DownReason`/`DownHealth`, test boilerplate.

## Reading order

Go top-down: contract → engine → wiring → indicators → docs. Read tests next to each class.

### 1. Public contract (decide first if the API is right; everything else follows)

| File | Look for |
|---|---|
| `health/contributor/HealthIndicator.java`, `ReactiveHealthIndicator.java` | Must be identical to `main` after `016a79bcc53`. Verify: `git diff fd867acba54 4382216a0e7 -- '**/contributor/HealthIndicator.java' '**/contributor/ReactiveHealthIndicator.java'`. |
| `health/contributor/TimeoutAwareHealthIndicator.java` | New sub-interface: abstract `health(Duration) throws TimeoutException`, default `getTimeoutEnforcement()` = `FRAMEWORK`, default `health(Duration, boolean)` (bypasses a `health(boolean)` override; documented). |
| `health/contributor/TimeoutAwareReactiveHealthIndicator.java` | Same, Mono variant. Plus `asHealthContributor()` returning the timeout-aware adapter. |
| `health/contributor/TimeoutEnforcement.java` | Public enum `INDICATOR`/`FRAMEWORK`. Naming. Is a 2-value public enum needed? |
| `AbstractTimeoutAwareHealthIndicator` / `...ReactiveHealthIndicator` | New public base classes implementing the sub-interfaces; `final` on `doHealthCheck(Builder)`, `health(Duration)`, `getTimeoutEnforcement()`. `timeout` is `@Nullable` in `doHealthCheck(builder, timeout)`. |
| `HealthIndicatorTimeouts.java` | Public class + public `InvalidTimeoutException`. Should this be public? |
| `HealthIndicatorExecutor` / `ReactiveHealthIndicatorExecutor` | Public, `@ConditionalOnMissingBean` beans → user-replaceable. Intended? |
| `core/.../thread/Threading.java` | New `isActive(PropertyResolver)`; abstract method signature changed, `Environment` overload now concrete. |
| Visibility | `AbstractHealthIndicator.logExceptionIfPresent`, `AbstractReactiveHealthIndicator.logExceptionIfPresent/handleFailure`: private → package-private. |

Questions:
- Plain indicators: called via `health(includeDetails)` exactly as before, so their `health(boolean)` overrides survive. Check the executor `instanceof` branches.
- Timeout-aware indicators: default `health(Duration, boolean)` skips `health(boolean)`. Only affects new opt-in code; documented on the interface. No production class overrides `health(boolean)` except the adapters. No default can honor both (`health(boolean)` has no timeout); only fix is making `health(Duration, boolean)` abstract.
- History: an earlier sub-interface design (`32a99118c79`) was dropped in `bc1354806a9` because plain indicators *ignored* timeouts. Now plain indicators fall back to `FRAMEWORK`, which removes that reason.

### 2. Engine (most risk; spend most time here)

Read in this order:

1. `DownReason`, `DownHealth`, `ThreadingMode` — trivial, 5 min.
2. `HealthIndicatorTimeouts` — name mapping (`/` → `.`, lowercased), caching in `ConcurrentHashMap` (never invalidated: no refresh on env change), zero/negative rejected.
3. Executors' `instanceof TimeoutAware…` split — plain → `FRAMEWORK` + `health(includeDetails)`; aware → `getTimeoutEnforcement()` + `health(timeout, includeDetails)`.
4. `InFlightExecutions` — **concurrency core**. Check:
   - `compute()` lambda throws `TooManyChecksInFlightException` and calls `starter.get()` inside `ConcurrentHashMap.compute` (side effects in compute; executor submit happens later in `startJoinable`, OK?).
   - Slot accounting: every `tryAcquire` matched by exactly one `release` on all paths (start failure, create failure, normal end, timeout).
   - `Execution.canJoin`: `now - deadline < 0` (overflow-safe). Joiners after deadline start a new one.
   - `executions` map entries are never removed on normal completion — only replaced. Leak per key is bounded (1 per key), fine?
   - Key = (name, includeDetails) → doc claim "8 threads per indicator".
5. `HealthIndicatorExecutor` — check:
   - `AsyncCheck.start`: `orTimeout` on result, `cancel(true)` on timeout. `running` CAS: if task not yet started, `onEnd` runs from timeout; if running, slot held until check returns. Matches doc WARNING.
   - Pool: `ThreadPoolExecutor(0, MAX_VALUE, SynchronousQueue)` — bounded only by InFlightExecutions × number of indicator names. Many indicators (composites) × 8?
   - `executeWithoutDeadline` with `POOL` uses `inFlight.start` (no join) — limit applies even without timeout for reactive-adapted blocking indicators.
   - `destroy()` → `shutdownNow`; later calls → `DISPOSED`.
   - Virtual threads via reflection (`newVirtualThreadPerTaskExecutor`) because of `--release 17`.
   - `Contributor.Blocking.getDescriptor` does `.join()` — request thread still blocked, but bounded.
6. `ReactiveHealthIndicatorExecutor` — `SharedCheck` uses `Sinks.one()` + `subscribe()` eagerly (hot). Check:
   - Cancellation of a downstream subscriber doesn't cancel the shared upstream (by design?).
   - `timeout == null` path: no safeguard against concurrency, no sharing.
   - `InvalidTimeoutException` thrown from `timeouts.get` inside `Mono.defer` → caught by `safeguard`.
   - `Mono.timeout` default scheduler = `Schedulers.parallel()`.
7. Adapters:
   - `HealthIndicatorAdapter` (blocking → reactive): no timeout methods; reactive executor unwraps it and runs the delegate on the blocking executor (`POOL`). Only path in Boot: `ReactiveHealthContributor.adapt(...)`. Direct callers outside the executor get 4.0 behaviour (`boundedElastic`, no timeout).
   - `ReactiveHealthIndicatorAdapter` (reactive → blocking): plain `HealthIndicator`, back to its 4.0 shape (`4382216a0e7`).
   - `TimeoutAwareReactiveHealthIndicatorAdapter extends ReactiveHealthIndicatorAdapter implements TimeoutAwareHealthIndicator`: created by `TimeoutAwareReactiveHealthIndicator#asHealthContributor()`, forwards enforcement + timeout. All reactive → blocking paths (`Contributor.blocking`, `GrpcServerHealth`, composites) go through `asHealthContributor()`.
   - `findTimeout` unwrapping loop (now in the timeout-aware adapter only).

Tests to read alongside: `ExecutorTestSupport`, `InFlightExecutionsTests`, `HealthIndicatorExecutorTests` (incl. `shouldPassTimeoutToJdkProxyOfTimeoutAwareIndicator`), `ReactiveHealthIndicatorExecutorTests`, `TimeoutAware(Reactive)HealthIndicatorTests`, `(TimeoutAware)ReactiveHealthIndicatorAdapterTests`, `HealthIndicatorAdapterTests`. Check for `Thread.sleep` (use Awaitility), flaky timing, latches always released.

Test gaps:
- No end-to-end executor test with an *adapted* timeout-aware indicator, either direction (timeout passed, `INDICATOR` not capped). Pieces are tested in isolation.
- `checkArchitectureTestJava` fails: `AbstractTimeoutAwareReactiveHealthIndicatorTests` calls `verifyComplete()` 4× (use `expectComplete().verify(Duration)`).

### 3. Wiring

| File | Look for |
|---|---|
| `HealthContributorRegistryAutoConfiguration` | New beans `HealthIndicatorExecutor` (`DisposableBean`), `ReactiveHealthIndicatorExecutor`, `HealthIndicatorTimeoutValidator`. Validator has no `@ConditionalOnMissingBean`. |
| `HealthIndicatorTimeoutValidator` | Fails startup on bad timeout. Creates its own `HealthIndicatorTimeouts` (separate cache). Only validates registered names — typo'd property names pass silently. Composite `db` timeout does NOT apply to `db/primary` children: intended? |
| `HealthEndpointConfiguration`, `HealthEndpointWebExtensionConfiguration`, `HealthEndpointReactiveWebExtensionConfiguration` | Pass executor. |
| `HealthEndpoint`, `HealthEndpointWebExtension`, `ReactiveHealthEndpointWebExtension` | Deprecated old ctors + duplicated `noopPropertyResolver()` ×3. |
| `Contributor`, `HealthEndpointSupport` | `getDescriptor(name, ...)` signature change; name = full path. |
| `CloudFoundryWebEndpointDiscovererTests`, `HealthEndpointDocumentationTests` | Ctor adaptation only. |

### 4. Indicators (mostly mechanical; batch by enforcement)

INDICATOR (no network or own timeout):
- `PingHealthIndicator`, `AvailabilityStateHealthIndicator`, `SslHealthIndicator` (`implements TimeoutAwareHealthIndicator`)
- `CassandraDriver(Reactive)HealthIndicator`, `Couchbase(Reactive)HealthIndicator` (`implements TimeoutAware(Reactive)HealthIndicator`)
- `ElasticsearchRestClientHealthIndicator` (request cancellation; +mockwebserver test)
- `Mongo(Reactive)HealthIndicator` (`maxTimeMS`, ms rounding; new dockerTests)

FRAMEWORK + timeout passed to client:
- `DataSourceHealthIndicator` (`isValid(seconds)` / query timeout, rounded up)
- `HazelcastHealthIndicator` (transaction lifespan)
- `JmsHealthIndicator` (watchdog uses timeout or 5s)
- `Neo4j(Reactive)HealthIndicator` (tx timeout)

Other:
- `DataRedisReactiveHealthIndicator`: `flatMap+closeLater` → `Mono.usingWhen`. Unrelated fix (connection close on cancel)? Separate commit?
- `GrpcServerHealth` + auto-config: check what changed and why.

Per indicator check: rounding never shortens the limit; `null` timeout path unchanged; `@author` added.

Subclass break: `DataSourceHealthIndicator`, `JmsHealthIndicator`, etc. are public, non-final and moved to `AbstractTimeoutAware*` with `final doHealthCheck(Builder)` → external subclasses overriding it no longer compile.

### 5. Breaking changes / removals (verify none shipped in a release)

- `JmsHealthIndicatorProperties` deleted → `management.health.jms.start-timeout` gone. `@since 4.2.0` → OK only if 4.2.0 unreleased. Replaced by `management.health.jms.timeout` — semantics differ (also bounds whole check).
- `JmsHealthIndicator(ConnectionFactory, Duration)` ctor and `public DEFAULT_START_TIMEOUT` removed.
- `@author Venkata Naga Sai Srikanth Gollapudi` removed from `JmsHealthContributorAutoConfiguration`. Keep it.
- `Contributor.getDescriptor` signature change (package-private? check).
- `Threading.isActive(Environment)` no longer abstract — binary-compatible for callers.

### 6. Metadata & docs

- `additional-spring-configuration-metadata.json` in 14 modules: one `*.timeout` per indicator.
  - `management.health.rabbit.timeout` added in **both** `spring-boot-amqp-rabbitmq` and `spring-boot-rabbitmq` (and `rabbit.enabled` duplicated). Intended?
  - `management.health.mongodb.enabled` / `mongo.enabled` moved from `spring-boot-data-mongodb` to `spring-boot-mongodb`. Unrelated move?
  - Couchbase `management.health.couchbase.timeout`: deprecation (error, since 2.0.6) removed, re-purposed as "not used". Cassandra same "not used" wording. Why expose a no-op property?
  - Missing? Check every auto-configured key in docs table has a `.timeout` entry (e.g. `redis` blocking, `ldap`, `mail`, `r2dbc`, `diskspace`).
- `endpoints.adoc`: new section `actuator.endpoints.health.timeouts` (+ unrelated r2dbc row added to reactive table). Verify claims against code: "four", "eight threads", "rounded up", "calling thread". Admonitions preceded by blank line.
- Docs samples `MyHealthIndicator` / `MyClient` (Java + Kotlin): compile, match recommended pattern. `doHealthCheck(builder, Duration timeout)` lacks `@Nullable` although the base class declares it and docs say it is `null` when none is configured.
- "Writing Timeout-aware HealthIndicators" section must describe the sub-interfaces (`016a79bcc53`), not methods on `HealthIndicator`.
- `spring-boot-dependencies/build.gradle`: javadoc links for Cassandra driver + reactor-core (needed by adoc `javadoc:` macros).

## Suggested sessions

1. Contract (§1) — 30 min. Decide on API shape before reading the rest.
2. Engine (§2) — 2 h. Read with tests; sketch the slot state machine on paper.
3. Wiring + breaking changes (§3, §5) — 30 min.
4. Indicators (§4) — 1 h, one batch at a time.
5. Metadata + docs (§6) — 30 min; run `g antora` for rendering.

## Commands

```bash
R='fd867acba54 4382216a0e7'   # base and tip of the branch
X=(':!review-map-f8a1345.md' ':!health-timeouts-slides.md' ':!slides.txt')
git diff --stat=250 $R -- . "${X[@]}"
git diff $R -- module/spring-boot-health/src/main                      # engine + contract
git diff $R -- 'module/*/src/main/java/**/health/*Indicator.java'      # indicators
git diff $R -- '*.json'                                                # metadata
git diff $R -- '*.adoc'
git show 016a79bcc53                                                   # sub-interface refactor alone
git show 4382216a0e7                                                   # adapter types alone
rg -n 'Thread.sleep' $(git diff --name-only $R | rg 'Test')            # test hygiene
g :module:spring-boot-health:test
```
