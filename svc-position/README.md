# svc-position

The `svc-position` service is a part of a larger application
that handles financial transactions and market data.

This service is responsible for computing and managing positions based
on transaction data retrieved from the `svc-data` service.

## Features

- Computes positions based on transaction data.
- Provides APIs to retrieve current positions for a given portfolio.
- Calculates ROI, Market Value, and Unrealized P&L for a given portfolio.
- Multi-currency support.
- **Contract Testing**: Generates and publishes contract stubs for other services.

## Building

### Local Development

```bash
# Build this service
./gradlew :svc-position:build

# Run tests
./gradlew :svc-position:test

# Publish contract stubs to local Maven repository
./gradlew :svc-position:pubStubs
```

### Contract Stubs

This service exposes its Spring Cloud Contract stubs as a Gradle `stubs`
configuration (built from `verifierStubsJar`), consumed by svc-event via
`testImplementation(project(path = ":svc-position", configuration = "stubs"))`.
Gradle builds the stubs automatically before any consumer tests run — no
manual publishing needed. `./gradlew :svc-position:pubStubs` still publishes to
`~/.m2` if an external consumer ever needs it.

### Docker

```bash
# Build Docker image
./gradlew :svc-position:bootBuildImage
```
