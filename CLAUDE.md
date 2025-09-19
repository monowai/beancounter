# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a financial transaction processing service built with Kotlin and Spring Boot. It transforms financial transaction data into portfolio positions for valuation against market data. The project consists of multiple interconnected services with circular dependencies that require careful build management.

## Critical Build Information

### ⚠️ Circular Dependencies

This project has circular dependencies that **will cause clean builds to fail**. This is expected behavior. Services depend on each other's contract stubs, creating a chicken-and-egg problem.

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
- **jar-shell**: Command-line interface (depends on svc-data stubs)

### Services (Build Order: 2nd)
- **svc-data**: Data persistence and market data services (publishes stubs)
- **svc-position**: Portfolio position calculations (depends on svc-data stubs, publishes stubs)
- **svc-event**: Corporate event processing (depends on both svc-data and svc-position stubs)
- **svc-agent**: AI agent service for chat functionality

### Contract Testing

Uses Spring Cloud Contract with hybrid approach:
- **Shared Context** (default): ~4-6x faster, shared Spring context
- **Isolated Context** (Kafka tests): Complete isolation, fixed ports 11999/12999
- Port allocation for shared context: jar-client(10990), jar-shell(10991), svc-event(10992), svc-position(10993)

## Deployment

**Production Environment**: `kauri.monowai.com`
- **Orchestration**: Kubernetes with Helm charts (`../bc-deploy/`)
- **Database**: PostgreSQL hosted on kauri.monowai.com
- **Configuration**: `../bc-deploy/env/minikube.yaml` contains service configurations
- **Secrets**: `../bc-deploy/.env` contains integration tokens (⚠️ use as variables, never expose)
- **Helm Charts**: Individual charts in `../bc-deploy/charts/` (bc-data, bc-position, bc-event, bc-view)

## CI/CD Pipeline (CircleCI)

The project uses CircleCI with optimized build pipelines:
- **build-and-test**: Complete build handling circular dependencies and stub publishing
- **Docker packaging**: Multi-platform images (linux/amd64, linux/arm64) published to GitHub Container Registry
- **Coverage reporting**: Codecov and Codacy integration
- **Branch filters**: Docker builds only on `master` and `/^mike\/.*/` branches
- **Container registry**: `ghcr.io/monowai/` (bc-shell, bc-data, bc-position, bc-event)

## Development Workflow

1. **Daily Development**: Use `./gradlew buildSmart` and `./gradlew testSmart` - these check for stubs first
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

## OpenAPI Documentation

Services expose OpenAPI specifications via SpringDoc:
- **SpringDoc version**: 2.8.8 (configured in `gradle/libs.versions.toml`)
- **Configuration**: `springdoc.use-management-port: true` - OpenAPI docs served on management port
- **Access URLs**: 
  - svc-data: http://localhost:9511/swagger-ui.html (management port)
  - svc-position: http://localhost:9501/swagger-ui.html (management port)
  - API docs available at `/v3/api-docs` endpoint
- **Authentication**: API endpoints require JWT token from `BC_TOKEN`

## Technology Stack

- **Language**: Kotlin with Java 21
- **Framework**: Spring Boot 3.x
- **Build Tool**: Gradle 8.14+
- **Testing**: JUnit 5, Spring Cloud Contract, AssertJ
- **Code Quality**: Kotlinter (formatting), Detekt (static analysis)
- **API Documentation**: SpringDoc OpenAPI 3

## Important Files

- `BUILD_PROCESS.md`: Detailed build process and stub management
- `CONTRACT_TEST_ARCHITECTURE.md`: Contract testing approach and configuration
- `build-with-stubs.sh`: Script for clean builds handling circular dependencies