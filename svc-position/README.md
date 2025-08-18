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

This service generates contract stubs that are used by other services for testing:

- **Stub Artifact**: `org.beancounter:svc-position:0.1.1:stubs`
- **Local Path**: `~/.m2/repository/org/beancounter/svc-position/0.1.1/`

The stubs are automatically published when using the smart build tasks:

```bash
# Smart build (publishes stubs if needed)
./gradlew buildSmart

# Complete build (always publishes stubs)
./gradlew buildWithStubs
```

### Docker

```bash
# Build Docker image
./gradlew :svc-position:bootBuildImage
```
