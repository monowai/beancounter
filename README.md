# Financial Transaction Processing Services

This service transforms financial transaction data into portfolio positions for
valuation against market data.

Check out the [demo stack](http://github.com/monowai/bc-demo).

## Components

- [Viewer](https://github.com/monowai/bc-view): User interface.
- [Data Service](svc-data/README.md): Data persistence and retrieval.
- [Position Service](svc-position/README.md): Computes portfolio positions.
- [Corporate Events](svc-event/README.md): Manages corporate actions.

## Quick Start

### Prerequisites

- Java 21
- Gradle 8.14+
- Docker (for containerized services)

### Building the Project

#### Standard Build

Contract stubs flow between modules as regular Gradle artifacts (svc-data and
svc-position expose a `stubs` configuration consumed by jar-client, jar-shell,
svc-position and svc-event), so a plain build works from a clean checkout:

```bash
# Build everything (clean checkout OK)
./gradlew build

# Run all tests
./gradlew testAll
```

#### Individual Module Builds

```bash
# Build specific module (Gradle builds any stub producers it needs first)
./gradlew :jar-client:build
./gradlew :svc-data:build
```

#### Stub Management

```bash
# Optionally publish contract stubs to ~/.m2 (not required for building)
./gradlew publishStubs
```

#### Utility Tasks

```bash
# Clean all projects
./gradlew cleanAll

# Validate dependencies
./gradlew validateDependencies

# Format Kotlin code
./gradlew formatKotlin

# Lint Kotlin code
./gradlew lintKotlin
```

## Project Structure

### Core Libraries

- **jar-common**: Shared utilities, models, and contracts
- **jar-auth**: Authentication and authorization
- **jar-client**: Client libraries for service communication
- **jar-shell**: Command-line interface

### Services

- **svc-data**: Data persistence and market data services
- **svc-position**: Portfolio position calculations
- **svc-event**: Corporate event processing

### Contract Testing

The project uses Spring Cloud Contract for contract testing with a hybrid approach:

- **Shared Context**: Most tests share Spring context for ~4-6x faster execution
- **Isolated Context**: Complex tests (like Kafka) use isolated contexts for reliability

See [CONTRACT_TEST_ARCHITECTURE.md](CONTRACT_TEST_ARCHITECTURE.md) for detailed information.

## Development

### Running Tests

```bash
# Run all tests
./gradlew testSmart

# Run specific module tests
./gradlew :jar-client:test
./gradlew :svc-data:test

# Run with coverage
./gradlew testSmart jacocoTestReport
```

### Code Quality

```bash
# Format code
./gradlew formatKotlin

# Lint code
./gradlew lintKotlin

# Check for issues
./gradlew check
```

### Docker Builds

```bash
# Build Docker images
./gradlew :svc-data:bootBuildImage
./gradlew :svc-position:bootBuildImage
./gradlew :svc-event:bootBuildImage
```

## CI/CD

The project uses CircleCI with optimized build pipelines:

- **build-and-test**: Single `./gradlew build` (stub ordering handled by Gradle)
- **package-***: Creates Docker images (main branch only)

See [.circleci/config.yml](.circleci/config.yml) for configuration details.
