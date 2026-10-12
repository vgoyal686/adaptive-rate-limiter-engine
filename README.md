# Adaptive Rate Limiter Engine

A low-latency, single-node rate limiter built with Java 17 and Spring Boot. It
uses an in-memory adaptive token bucket, has no database dependency, and can
adjust each client's effective rate from observed latency and failures.

## Features and requirements

### Functional requirements

- Independent limits by policy and caller key.
- Weighted requests (`cost`) for operations of different sizes.
- Runtime policy create/update/read/delete APIs.
- Feedback API for request latency and success/failure.
- Automatic rate reduction during overload and gradual recovery when healthy.
- Per-key counters, effective rate, token balance, latency, and error statistics.
- Standard HTTP `429` and `Retry-After` response when a request is rejected.
- A configurable default policy available at startup.

### Non-functional requirements

- **Low latency:** one `ConcurrentHashMap` lookup and one per-key lock on the hot
  path; unrelated keys do not contend.
- **Thread safety:** token refill, consumption, and adaptation are atomic for a key.
- **Bounded memory:** configurable bucket limit plus scheduled idle-key eviction.
- **Numerical stability:** refill uses monotonic nanosecond time and fractional tokens.
- **Availability:** invalid traffic cannot create buckets; capacity exhaustion fails
  with `503` instead of consuming unbounded heap.
- **Observability:** statistics API and Spring Actuator health/metrics endpoints.
- **Maintainability:** controller, service, model, repository, configuration, and
  exception packages with unit and API tests.

## How adaptation works

Each `(policyId, key)` has a token bucket. Tokens refill at that bucket's current
effective rate up to the configured burst capacity. After each feedback window:

1. If average latency exceeds the target or error rate exceeds the threshold,
   the rate is reduced multiplicatively.
2. Otherwise, the rate increases additively toward the configured maximum.
3. The minimum and maximum rates are always enforced.

This AIMD-style behavior reacts quickly to downstream pressure and recovers
gradually to avoid a traffic surge. Enforcement and feedback are separate so
only completed downstream calls influence adaptation.

## Project structure

```text
src/main/java/com/example/ratelimiter
├── config       # Type-safe application configuration
├── controller   # REST API and request DTOs
├── exception    # API error mapping
├── model        # Policies, bucket state, decisions, and statistics
├── repository   # In-memory repository contracts and implementations
└── service      # Rate limiting and policy orchestration
```

## Run and test

Prerequisites: JDK 17 and Maven 3.6.3 or newer.

```bash
mvn clean verify
mvn spring-boot:run
```

The service listens on `http://localhost:8080`.

## API examples

Acquire one token:

```bash
curl -i -X POST http://localhost:8080/api/v1/policies/default/acquire \
  -H "Content-Type: application/json" \
  -d '{"key":"customer-42","cost":1}'
```

Submit downstream feedback:

```bash
curl -i -X POST http://localhost:8080/api/v1/policies/default/feedback \
  -H "Content-Type: application/json" \
  -d '{"key":"customer-42","latencyMs":85,"successful":true}'
```

Create or replace a policy:

```bash
curl -X PUT http://localhost:8080/api/v1/policies/payments \
  -H "Content-Type: application/json" \
  -d '{
    "id":"payments",
    "capacity":50,
    "initialRatePerSecond":100,
    "minimumRatePerSecond":10,
    "maximumRatePerSecond":500,
    "targetLatencyMs":150,
    "maximumErrorRate":0.02,
    "evaluationWindow":50,
    "adaptationFactor":0.20
  }'
```

Inspect state and policies:

```bash
curl "http://localhost:8080/api/v1/policies/default/stats?key=customer-42"
curl http://localhost:8080/api/v1/policies
curl http://localhost:8080/actuator/health
```

## Configuration

Key settings in `application.properties`:

- `rate-limiter.maximum-buckets`: hard target for active in-memory keys.
- `rate-limiter.idle-ttl`: inactivity duration before a bucket is eligible for removal.
- `rate-limiter.cleanup-interval-ms`: idle-bucket cleanup frequency.
- `rate-limiter.default-policy.*`: startup policy and adaptation parameters.

Tune heap size, maximum buckets, Tomcat threads, and policy windows with realistic
load tests. Smaller feedback windows react faster but are more sensitive to noise.

## Important deployment limitations

State is local and intentionally non-durable. A restart resets all buckets and
statistics. Multiple application replicas enforce independent limits, so their
combined allowance is approximately the sum of each replica's allowance. Use
sticky routing or divide policy rates across replicas when deploying this version
horizontally. A globally consistent distributed limit requires shared coordination,
which is outside this no-database design.

Policy administration endpoints do not include authentication. Protect them with
an API gateway or Spring Security before exposing the service outside a trusted
network.
