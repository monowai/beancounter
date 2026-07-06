# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this
repository.

## Project Overview

This is a financial transaction processing service built with Kotlin and Spring Boot. It transforms
financial transaction data into portfolio positions for valuation against market data.

## Critical Build Information

### Contract stubs are Gradle artifacts

Spring Cloud Contract stubs flow between modules as regular Gradle project
artifacts: svc-data and svc-position each expose a `stubs` configuration
(built from `verifierStubsJar`), consumed via
`testImplementation(project(path = ..., configuration = "stubs"))` by
jar-client, jar-shell, svc-position and svc-event. Stub-runner tests use
`StubsMode.CLASSPATH` with ids like `beancounter:svc-data:0.1.1:stubs:10990`.
Gradle orders producer before consumer automatically — clean builds just work;
no ~/.m2 stub publishing, no manual build phases.

**Guardrail:** svc-data must NOT depend on `:jar-client` — that edge
re-creates the old circular build dependency (jar-client tests consume
svc-data's stubs). The shared ingest/SPI surface (`com.beancounter.client.*`
interfaces, `ingest/`, `sharesight/`) lives in **jar-common** for exactly this
reason; `validateDependencies` enforces the rule.

### Common Build Commands

```bash
# Build everything (clean checkout OK)
./gradlew build

# Run all tests incl. contract tests (buildSmart/testSmart remain as aliases)
./gradlew testAll

# Code quality
./gradlew formatKotlin
./gradlew lintKotlin
./gradlew check
```

### Stub Management

```bash
# Optionally publish stubs to ~/.m2 (not required for building or testing)
./gradlew publishStubs
```

## Architecture

### Core Libraries (Build Order: 1st)

- **jar-common**: Shared utilities, models, and contracts
- **jar-auth**: Authentication and authorization
- **jar-client**: Client libraries for service communication (depends on svc-data stubs)
- **jar-shell**: Command-line interface (depends on svc-data stubs) not in active use.

### Services (Build Order: 2nd)

- **svc-data**: Data persistence and market data services (publishes stubs)
- **svc-position**: Portfolio position calculations (depends on svc-data stubs, publishes stubs)
- **svc-event**: Corporate event processing (depends on both svc-data and svc-position stubs)
- **svc-agent**: AI agent service for chat functionality (in development)

### Contract Testing

Uses Spring Cloud Contract testing. code for the contracts exists in {service}/src/contractTest

## Messaging Architecture (Spring Cloud Stream)

The project uses **Spring Cloud Stream** with the functional programming model for broker-agnostic
messaging. Default broker is **RabbitMQ**. Kafka can be used via configuration.

### Message Flows

```
Price Updates:
  External Provider → svc-data (priceConsumer) → Database
                   ↓ (if dividend/split)
                   EventProducer → corporateEvent-out-0 → bc-ca-event-dev
                   ↓
                   svc-event (eventProcessor) → EventPublisher
                   ↓
                   transactionEvent-out-0 → bc-trn-event-dev
                   ↓
                   svc-data (trnEventConsumer) → Database

Transaction Import:
  jar-shell (csvImport-out-0) → bc-trn-csv-dev
                   ↓
                   svc-data (csvImportConsumer) → Database

Portfolio Updates:
  svc-position (portfolioMarketValue-out-0) → bc-pos-mv-dev
                   ↓
                   svc-data (portfolioConsumer) → Database
```

### Functional Consumers and Producers

**svc-data consumers:**

- `csvImportConsumer`: Consumer&lt;TrustedTrnImportRequest&gt; - Processes CSV transaction imports
- `trnEventConsumer`: Consumer&lt;TrustedTrnEvent&gt; - Processes transaction events
- `priceConsumer`: Consumer&lt;PriceResponse&gt; - Persists market data prices
- `portfolioConsumer`: Consumer&lt;Portfolio&gt; - Maintains portfolio data from position updates

**svc-data producers:**

- `EventProducer` (StreamBridge): Publishes corporate action events via `corporateEvent-out-0`

**svc-position producers:**

- `MarketValueUpdateProducer` (StreamBridge): Publishes portfolio updates via
  `portfolioMarketValue-out-0`

**svc-event:**

- `eventProcessor`: Consumer&lt;TrustedEventInput&gt; - Processes corporate action events
- `EventPublisher` (StreamBridge): Publishes transaction events via `transactionEvent-out-0`

**jar-shell:**

- `KafkaTrnProducer` (StreamBridge): Sends CSV imports via `csvImport-out-0` (disabled by default)

### Service Configuration

Bindings are configured in each service's `application.yml`:

```yaml
spring.cloud.stream:
    function.definition: csvImportConsumer;trnEventConsumer;priceConsumer;portfolioConsumer
    bindings:
        csvImportConsumer-in-0:
            destination: bc-trn-csv-dev # Kafka topic name
            group: bc-data # Consumer group
            content-type: application/json
    kafka.binder.brokers: kafka:9092
```

### Topic Naming Convention

Topics follow the pattern: `bc-{service}-{type}-{env}`.

| Suffix  | Stack            | Where set                                             |
|---------|------------------|-------------------------------------------------------|
| `-dev`  | localhost dev    | default in each `application.yml`                     |
| `-demo` | kauri (deployed) | overridden via env vars in `bc-deploy/env/kauri.yaml` |

The two suffixes keep local and deployed flows from cross-talking on a shared broker.
Values shown below are the in-repo defaults (`-dev`):

- `bc-pos-mv-dev` - Portfolio market values
- `bc-trn-csv-dev` - CSV transaction imports
- `bc-trn-event-dev` - Transaction events
- `bc-ca-event-dev` - Corporate action events
- `bc-price-dev` - Price data updates
- `bc-perf-invalidate-dev` - Performance cache invalidation

### Switching to Kafka

To switch from RabbitMQ to Kafka:

1. Replace `spring-cloud-stream-binder-rabbit` with `spring-cloud-stream-binder-kafka` in
   build.gradle.kts
2. Update `spring.cloud.stream.rabbit.binder.*` to `spring.cloud.stream.kafka.binder.*`
3. No code changes required - same functional beans work with both brokers

### Testing

- **Production**: Uses RabbitMQ binder
- **Testing**: Uses `spring-cloud-stream-test-binder` for broker-agnostic unit tests

## Market Data Architecture

- `MarketDataPriceProcessor.getPriceResponse()` is the main price fetch entry point — checks DB
  first, then calls provider APIs for missing prices
- Price lookups are provider-agnostic: queries match by `(asset_id, priceDate)` regardless of source
- `MdFactory.resolveProvider()` iterates providers in map order: Cash → MarketStack → Alpha →
  Private → Morningstar
- Default provider config (application.yml): Alpha handles `US,NASDAQ,AMEX,NYSE,LON`; MarketStack
  handles `NZX,SGX`
- `AlphaCorporateEventEnricher` calls AlphaVantage API per asset — only meaningful for current-mode
  prices, not historical backfill
- `AssetCategory` constructor requires both `id` and `name` parameters (e.g.,
  `AssetCategory("cash", "Cash")`)
- **Stock splits**: see [SPLITS.md](SPLITS.md) before adding a new market-data provider —
  spec covers how `SplitAdjuster` rebases history, where event metadata comes from, and the
  invariants every adapter must hold (raw OHLC in DB, exact-date split match, single ex-date
  marker per event)

## Local Service Ports

| Service      | API Port | Management Port | OpenAPI Specs                            |
|--------------|----------|-----------------|------------------------------------------|
| svc-data     | 9510     | 9511            | <http://localhost:9511/actuator/openapi> |
| svc-position | 9500     | 9501            | <http://localhost:9501/actuator/openapi> |
| svc-event    | 9520     | 9521            | <http://localhost:9521/actuator/openapi> |
| svc-agent    | 9530     | 9531            | -                                        |

## Development Workflow

1. **Daily Development**: `./gradlew build` / `./gradlew testAll` (clean checkout OK;
   `buildSmart`/`testSmart` remain as aliases)
2. **Before Committing**: Run `./gradlew check` for code quality
3. **Code Formatting**: Auto-format with `./gradlew formatKotlin`

## Authentication

Services require JWT token authentication via Auth0:

- **Environment Variable**: `BC_TOKEN` - JWT token for API access
- **Token Format**: Bearer token (automatically prefixed)
- **Common Issues**: 401/403 errors indicate expired token - refresh from Auth0
- **Token Usage**: Include in Authorization header: `Authorization: Bearer {token}`
- **Auth0 Setup**: See `svc-agent/AUTH0_SETUP.md` for configuration details

## Technology Stack

- **Language**: Kotlin with Java 21
- **Framework**: Spring Boot 3.x
- **Build Tool**: Gradle 8.14+
- **Testing**: JUnit 5, Spring Cloud Contract, AssertJ
- **Code Quality**: Kotlinter (formatting), Detekt (static analysis)
- **API Documentation**: SpringDoc OpenAPI 3

## Database Migrations (Flyway)

The project uses **Flyway** for database schema migrations.

### Migration Locations

- `svc-data/src/main/resources/db/migration/` - PostgreSQL migrations
- `svc-event/src/main/resources/db/migration/` - PostgreSQL migrations (when using PostgreSQL)

### DB Configuration

- **Production/Local PostgreSQL**: Flyway enabled, runs migrations on startup
- **H2 Testing**: Flyway disabled (H2 uses Hibernate DDL auto-generation)

### Local Development Setup

For local development with PostgreSQL:

```bash
# Create local databases (one-time)
psql -U postgres -c "CREATE DATABASE \"bc-dev\";"
psql -U postgres -c "CREATE DATABASE \"ev-dev\";"

# Optional: Copy production data
pg_dump -h kauri.monowai.com -U postgres bc | psql -U postgres bc-dev
pg_dump -h kauri.monowai.com -U postgres ev | psql -U postgres ev-dev
```

Run services with local profile: `--spring.profiles.active=local`

## Enum Storage

### TrnType and TrnStatus

Both `TrnType` and `TrnStatus` enums are stored as **STRING** values in the database using
`@Enumerated(EnumType.STRING)`. This was migrated from ordinal storage via Flyway V1.

**Benefits:**

- Human-readable database values (e.g., "BUY", "SELL" instead of 0, 1)
- New enum values can be added anywhere (order doesn't matter for persistence)
- No risk of data corruption from enum reordering

**Adding new enum values:**

- New values can be added anywhere in the enum definition
- Adding at the end is still recommended for consistency
- No database migration needed for additions (STRING storage handles new values automatically)

## Related Repositories

For cross-repository work (debugging message flows, tracing requests, architectural changes):

- **`../bc-claude/CLAUDE.md`**: Ecosystem overview, system architecture, Auth0 config, message flows
- **`../bc-deploy/CLAUDE.md`**: Production deployment (kauri.monowai.com), Kubernetes/Helm
- **`../bc-view/CLAUDE.md`**: Next.js frontend

# Test-Driven Development

TDD is mandatory. Red → Green → Refactor → Lint. See
[`../bc-claude/SERVICE_DESIGN.md`](../bc-claude/SERVICE_DESIGN.md#test-driven-development-tdd)
for the full workflow, coverage targets, and test-behaviour-not-implementation
guidance — it applies unchanged here.

## Pre-Commit Checklist (beancounter-specific)

- [ ] All tests passing: `./gradlew testSmart` (alias of `testAll`)
- [ ] New functionality has tests
- [ ] Code formatted: `./gradlew formatKotlin`
- [ ] Linting passes: `./gradlew lintKotlin`