# svc-data

## Features

- Persists core BeanCounter data.
- Retrieves asset prices via various providers such
  as [Alpha Advantage](https://www.alphavantage.co/documentation/).
- Converts market codes as necessary.
- Provides APIs to request prices for a single asset or a collection of assets.
- **Contract Testing**: Generates and publishes contract stubs for other services.

## Configuration

You will need to register for an API key for each provider.
See the `application.yml` for how to set the key values.
Data providers can convert market codes as necessary.

## API Examples

This service returns market data and asset transactions for portfolios.
You can request prices for a single asset or a collection of them:

```bash
curl -X GET http://localhost:9510/AX/AMP
```

## Building

### Local Development

```bash
# Build this service
./gradlew :svc-data:build

# Run tests
./gradlew :svc-data:test

# Publish contract stubs to local Maven repository
./gradlew :svc-data:pubStubs
```

### Contract Stubs

This service exposes its Spring Cloud Contract stubs as a Gradle `stubs`
configuration (built from `verifierStubsJar`), consumed by jar-client, jar-shell and svc-position via
`testImplementation(project(path = ":svc-data", configuration = "stubs"))`.
Gradle builds the stubs automatically before any consumer tests run — no
manual publishing needed. `./gradlew :svc-data:pubStubs` still publishes to
`~/.m2` if an external consumer ever needs it.

### Docker

```bash
# Build Docker image
./gradlew :svc-data:bootBuildImage
```
