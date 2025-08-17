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

#### ⚠️ Important: Circular Dependencies

This project has circular dependencies that affect the build process. **Clean builds (Day 0) will fail** with dependency resolution errors. This is expected behavior.

#### Smart Build (Recommended for daily development)

```bash
# Fast build - checks for stubs first
./gradlew buildSmart

# Fast test - checks for stubs first
./gradlew testSmart
```

#### Complete Build (For CI/CD or clean builds)

```bash
# Manual build order to handle circular dependencies
./gradlew :jar-common:build :jar-auth:build
./gradlew :svc-data:build
./gradlew :svc-data:pubStubs
./gradlew :svc-position:build
./gradlew :svc-position:pubStubs
./gradlew :svc-event:build
./gradlew :jar-client:build :jar-shell:build

# Or use the build script
./build-with-stubs.sh
```

#### Individual Module Builds

```bash
# Build core libraries
./gradlew buildCore

# Build all services
./gradlew buildServices

# Build specific module
./gradlew :jar-client:build
./gradlew :svc-data:build
```

#### Stub Management

```bash
# Publish contract stubs
./gradlew publishStubs

# Verify stub availability
./gradlew verifyStubs
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

## Build Process

For detailed information about the build process and stub management, see [BUILD_PROCESS.md](BUILD_PROCESS.md).

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

- **build-core**: Builds core libraries and publishes stubs
- **build-services**: Tests services using published stubs
- **package-***: Creates Docker images (master branch only)

See [.circleci/config.yml](.circleci/config.yml) for configuration details.
