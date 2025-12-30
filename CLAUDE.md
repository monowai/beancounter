# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this
repository.

## Project Overview

This is a financial transaction processing service built with Kotlin and Spring Boot. It transforms
financial transaction data into portfolio positions for valuation against market data. The project
consists of multiple interconnected services with circular dependencies that require careful build
management.

## Critical Build Information

### ⚠️ Circular Dependencies

This project has circular dependencies that **will cause clean builds to fail**. This is expected
behavior. Services depend on each other's contract stubs, creating a chicken-and-egg problem.

### Common Build Commands

```bash
# For daily development (recommended - fast build when stubs exist)
./gradlew buildSmart
./gradlew testSmart

# Clean build from scratch (Day 0 - use this order)
./gradlew :jar-common:build :jar-auth:build
./gradlew :svc-data:build
./gradlew :svc-data:pubStubs
./gradlew :svc-position:build
./gradlew :svc-position:pubStubs
./gradlew :svc-event:build
./gradlew :jar-client:build :jar-shell:build

# Or use the build script for clean builds
./build-with-stubs.sh

# Testing (ensures stubs are available)
./gradlew testWithStubs

# Code quality
./gradlew formatKotlin
./gradlew lintKotlin
./gradlew check
```

### Stub Management

Contract stubs are published to local Maven repository (`~/.m2/repository`):

- svc-data stubs: Required by jar-client, jar-shell, svc-position, svc-event
- svc-position stubs: Required by svc-event

```bash
# Publish stubs
./gradlew publishStubs

# Verify stubs exist
./gradlew verifyStubs
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

Topics follow the pattern: `bc-{service}-{type}-{env}`

- `bc-pos-mv-dev` - Portfolio market values
- `bc-trn-csv-dev` - CSV transaction imports
- `bc-trn-event-dev` - Transaction events
- `bc-ca-event-dev` - Corporate action events
- `bc-price-dev` - Price data updates

### Switching to Kafka

To switch from RabbitMQ to Kafka:

1. Replace `spring-cloud-stream-binder-rabbit` with `spring-cloud-stream-binder-kafka` in
   build.gradle.kts
2. Update `spring.cloud.stream.rabbit.binder.*` to `spring.cloud.stream.kafka.binder.*`
3. No code changes required - same functional beans work with both brokers

### Testing

- **Production**: Uses RabbitMQ binder
- **Testing**: Uses `spring-cloud-stream-test-binder` for broker-agnostic unit tests

## Local Service Ports

| Service      | API Port | Management Port | OpenAPI Specs                            |
|--------------|----------|-----------------|------------------------------------------|
| svc-data     | 9510     | 9511            | <http://localhost:9511/actuator/openapi> |
| svc-position | 9500     | 9501            | <http://localhost:9501/actuator/openapi> |
| svc-event    | 9520     | 9521            | <http://localhost:9521/actuator/openapi> |
| svc-agent    | 9530     | 9531            | -                                        |

## Development Workflow

1. **Daily Development**: Use `./gradlew buildSmart` and `./gradlew testSmart` - these check for
   stubs first
2. **After Clean**: Use manual build order or `./build-with-stubs.sh`
3. **Before Committing**: Run `./gradlew check` for code quality
4. **Code Formatting**: Auto-format with `./gradlew formatKotlin`

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

## Important Files

- `build-with-stubs.sh`: Script for clean builds handling circular dependencies

# Test-Driven Development (TDD) Approach

## Mandatory TDD Workflow

When implementing new features or fixing bugs, ALWAYS follow this strict TDD cycle:

### 1. Understand Requirements First

- Clarify the feature/fix requirements before writing any code
- Ask questions if specifications are ambiguous
- Identify edge cases and expected behaviors

### 2. Write Tests FIRST (Red Phase)

- Write failing tests BEFORE implementing any functionality
- Tests should cover:
    - Happy path scenarios
    - Edge cases
    - Error conditions
    - Boundary values
- Use descriptive test names that explain the expected behavior
- Start with the simplest test case

### 3. Run Tests to Confirm Failure

- Verify that new tests fail for the right reasons
- Ensure test failure messages are clear and informative

### 4. Implement Minimal Code (Green Phase)

- Write the SIMPLEST code that makes the tests pass
- Don't add functionality that isn't tested
- Focus on making tests green, not on perfect code

### 5. Run Tests to Confirm Success

- All new tests should pass
- All existing tests should still pass (no regressions)

### 6. Refactor (Refactor Phase)

- Improve code quality while keeping tests green
- Remove duplication
- Improve cohesion - group related information by capability, not technical concerns.
- Improve naming and structure
- Run tests after each refactoring step

### 7. Repeat the Cycle

- Continue with the next smallest piece of functionality

## TDD Guidelines

### DO:

- Write tests that describe behavior, not implementation
- Keep tests focused and atomic (one assertion concept per test)
- Use meaningful test descriptions
- Mock external dependencies
- Test user-facing behavior over internal implementation
- Keep tests fast and independent

### DON'T:

- Write implementation code before tests
- Skip tests because "it's simple"
- Test implementation details
- Write tests that depend on other tests
- Leave failing tests uncommitted
- Mock everything (only mock boundaries)

## Test Coverage Expectations

- Aim for 80%+ code coverage
- 100% coverage for critical business logic
- Focus on meaningful coverage, not just hitting percentages
- Test edge case scenarios, minimum, mid and maximum.

## Checklist Before Committing

- [ ] All tests passing (`./gradlew testSmart`)
- [ ] New functionality has tests
- [ ] Code formatted (`./gradlew formatKotlin`)
- [ ] Linting passes (`./gradlew lintKotlin`)