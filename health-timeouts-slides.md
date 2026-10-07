---
marp: true
theme: default
paginate: true
size: 16:9
style: |
  section { font-size: 24px; }
  pre { font-size: 16px; line-height: 1.25; }
  table { font-size: 18px; }
  h1 { color: #6db33f; }
---

# Health indicator timeouts

Spring Boot 4.2 · branch `mh/43449-allow-health-indicators-to-run-with-a-specified-timeout`

Bound how long a single health check may take.

---

# Problem

One hung dependency hangs the whole health endpoint.

```
GET /actuator/health
   │
   ├─ db        ✔ 12 ms
   ├─ redis     ✔  3 ms
   └─ mail      … SMTP server not answering … 60 s … ∞
                     │
                     ▼
   K8s probe times out  →  pod restarted
   every probe parks one more request thread
```

Before: only client-level timeouts (socket, pool, driver), if configured at all.

---

# Configuration

```properties
management.health.defaults.timeout = 2s     # all indicators
management.health.db.timeout = 500ms        # db, or all db/* children
management.health.db.primary.timeout = 1s   # nested "db/primary"
```

Lookup for `db/primary`: `db.primary.timeout` → `db.timeout` → `defaults.timeout`, first wins.

On timeout, only that indicator is `DOWN`:

```json
{ "status": "DOWN",
  "components": {
    "db":   { "status": "UP" },
    "mail": { "status": "DOWN",
              "details": { "error": "java.util.concurrent.TimeoutException",
                           "reason": "timeout" } } } }
```

- No timeout configured → old behaviour (opt-in)
- Timeout ≤ 0 or unparseable → startup fails

---

# Two ways to enforce: `TimeoutEnforcement`

```
 INDICATOR                              FRAMEWORK (default)
 ─────────                              ───────────────────
 request thread                         request thread
     │                                      │ submit
     ▼                                      ▼
 indicator.health(timeout)              health-check-N thread / virtual thread
     │  hands timeout to client             │  plain:         indicator.health()
     │  (maxTimeMS, cancel request…)        │  timeout-aware: indicator.health(timeout)
     ▼                                      │◄── timeout elapsed: interrupt
 Health / TimeoutException                  ▼    + report DOWN "timeout"
                                        Health
```

| | INDICATOR | FRAMEWORK |
|---|---|---|
| Who stops the work | the client | `Future.cancel(true)` / `Mono.timeout` |
| Thread | caller | own pool (or virtual threads) |
| Used by | ping, ssl, *state, cassandra, couchbase, elasticsearch, mongodb | everything else, incl. user indicators |

---

# API: opt-in sub-interface

```
HealthIndicator                      (unchanged)
  └─ TimeoutAwareHealthIndicator     new, @since 4.2
       │  Health health(Duration timeout) throws TimeoutException   ← abstract
       │  TimeoutEnforcement getTimeoutEnforcement()  → FRAMEWORK   ← default
       └─ AbstractTimeoutAwareHealthIndicator
            doHealthCheck(builder, @Nullable Duration timeout)

ReactiveHealthIndicator → TimeoutAwareReactiveHealthIndicator → Abstract…   (mirror)
```

| Indicator | Timeout passed? | Who caps? |
|---|---|---|
| plain `HealthIndicator` | no, `health(includeDetails)` as before | framework |
| `TimeoutAware…`, `FRAMEWORK` | yes | framework |
| `TimeoutAware…`, `INDICATOR` | yes | the indicator |

---

# Where does my check run?

```
timeout configured?
 │
 ├─ no ───────────────────────────► NO DEADLINE
 │
 └─ yes ─ TimeoutAware…?
            │
            ├─ no ────────────────► CAPPED      health(details)
            │
            └─ yes ─ getTimeoutEnforcement()
                       ├─ INDICATOR ► NO DEADLINE health(timeout, details)
                       └─ FRAMEWORK ► CAPPED      health(timeout, details)
```

| | Servlet | WebFlux, blocking indicator | WebFlux, reactive indicator |
|---|---|---|---|
| NO DEADLINE | calling thread, no limit | pool, limit 4, not shared | subscribed as-is, no limit |
| CAPPED | pool + interrupt, limit 4, shared | pool + interrupt, limit 4, shared | `Mono.timeout`, limit 4, shared |

---

# Request flow

```
HealthEndpoint / WebExtension / ReactiveWebExtension
        │  name = full path, e.g. "db/primary"
        ▼
Contributor.getDescriptor(name, showDetails)
        │
        ▼
HealthIndicatorExecutor ─────────────── ReactiveHealthIndicatorExecutor
  │ 1. HealthIndicatorTimeouts                │ blocking indicator adapted?
  │    path, parents, then defaults           │   → delegate to blocking executor
  │ 2. instanceof TimeoutAware…?              │ else Mono + .timeout()
  │    no  → FRAMEWORK, health(details)       │   (same instanceof split)
  │    yes → getTimeoutEnforcement()          │
  │ 3. InFlightExecutions (limit + sharing)   │
  ▼                                           ▼
Health  ─── failures → DOWN + reason: timeout | concurrency-limit |
                       invalid-timeout | rejected | disposed | execution-failed
```

---

# Sharing and limits

Sharing (`FRAMEWORK` only): concurrent probes join the running check until its deadline.

```
time ─────────────────────────────────────────────────────►
probe A  ──start check #1──────────────┐ deadline (2s)
probe B      └─ joins #1 ──────────────┤  same result
probe C           └─ joins #1 ─────────┤
probe D                                │ └─ starts check #2
                                       ▼
```

- Max **4** concurrent executions per indicator, for every check on the pool:
  `FRAMEWORK`, and blocking indicators in WebFlux (even `INDICATOR` or no timeout)
- Counted separately for callers **with** and **without** details
  → unauthenticated callers can't exhaust the authenticated ones' slots
- Configurable: `management.health.concurrency-limit.detailed` / `.summary` (≥ 1)
- 5th → `DOWN reason=concurrency-limit`, no thread used
- No limit on the calling thread: servlet `INDICATOR` / no timeout, reactive `INDICATOR` / no timeout

---

# What it can't do

> A health timeout bounds the **reported result**, not the **work**.

```
check ignores interrupt (hung NFS, native call)
   │
   ├─ caller gets DOWN "timeout" after 2s   ✔
   └─ thread stays blocked until call returns ✘
          → max 4 + 4 = 8 parked threads per indicator, then concurrency-limit
```

- Still configure client timeouts (socket, pool, driver). Health timeout = safety net.
- Virtual threads: interrupt also unblocks `Socket` reads/writes.
- Shared checks run without the caller's context: no security context, MDC, Reactor context.

---

# Writing a timeout-aware indicator

```java
@Component
public class MyHealthIndicator extends AbstractTimeoutAwareHealthIndicator {

    public MyHealthIndicator(MyClient client) {
        super(TimeoutEnforcement.INDICATOR);   // "I bound the whole check"
        this.client = client;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder, @Nullable Duration timeout) {
        // timeout == null → none configured
        builder.status(this.client.ping(timeout) ? Status.UP : Status.DOWN);
    }
}
```

- Throw `TimeoutException` → `reason: "timeout"`
- `INDICATOR` but bounds only part (e.g. query, not connection)? → use `FRAMEWORK`, still receives the timeout
- No base class? Implement `TimeoutAwareHealthIndicator`: `health()` + `health(Duration)`, optionally `getTimeoutEnforcement()`

---

# Auto-configured indicators

| Mode | Indicator | Timeout applied as |
|---|---|---|
| INDICATOR | mongodb | `maxTimeMS` (ms, rounded up) |
| | elasticsearch | request cancellation |
| | cassandra, couchbase, ping, ssl, *state | not used (no I/O; property documented as "Not used") |
| FRAMEWORK + hint | db | `isValid(s)` / query timeout (s, rounded up) |
| | neo4j | transaction timeout (server-side) |
| | hazelcast | transaction lifespan (checked on commit only) |
| | jms | watchdog closes connection (default 5s) |
| FRAMEWORK | ldap, mail, diskspace, redis, rabbit, r2dbc, user | interrupt / `Mono.timeout` |

Breaking: `management.health.jms.start-timeout` → `management.health.jms.timeout`

---

# WebFlux: blocking indicators, before and after

Applies even with **no timeout configured**.

```
BEFORE                                   AFTER
──────                                   ─────
blocking indicator                       blocking indicator
   │ subscribeOn                            │ HealthIndicatorExecutor
   ▼                                        ▼
Schedulers.boundedElastic()              health-check pool (or virtual threads)
  shared with the whole app                health checks only
  10 × CPU threads                         no thread cap
  queues when busy                         no queue
                                           4 slots per indicator, per details flag
   │                                        │
   ▼                                        ▼
busy → waits in queue                    5th concurrent call → DOWN concurrency-limit
```

---

# Open questions

1. **Two paths:** Should we drop `INDICATOR` and use `FRAMEWORK` for everything?
1. **Default behaviour change:** In WebFlux, blocking indicators go through the
   new pool with the 4-slot limit **even without a timeout**
   → `concurrency-limit` under load where there was none before (was `boundedElastic`).
1. **No-op properties:** `cassandra`, `couchbase`, `ping`, `ssl`, `livenessstate`,
   `readinessstate` `.timeout` documented as "Not used". Drop them instead?
1. **Removals:** JMS `start-timeout`, ctor, `DEFAULT_START_TIMEOUT`. OK since 4.2 unreleased.
