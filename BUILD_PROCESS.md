# Build Process and Contract Stub Management

## Overview

This project uses Spring Cloud Contract for contract testing, which requires contract stubs to be published to the local Maven repository before tests can run. **Due to circular dependencies between services, clean builds require a specific approach.**

## ⚠️ Important: Circular Dependencies

The project has circular dependencies that affect the build process:

### Circular Dependency Chain

```
jar-client → svc-data:stubs → svc-data → jar-client
jar-shell → svc-data:stubs → svc-data → jar-shell  
svc-position → svc-data:stubs → svc-data → svc-position
svc-event → svc-position:stubs → svc-position → svc-event
```

### Impact

- **Clean builds** (Day 0) will fail with dependency resolution errors
- **Incremental builds** work fine when stubs already exist
- **CI/CD** requires special handling

## Build Dependencies

### Contract Stub Dependencies

The following services depend on contract stubs:

| Service | Stub Dependencies | Ports Used |
|---------|------------------|------------|
| **jar-client** | svc-data | 10990 |
| **jar-shell** | svc-data | 10991 |
| **svc-event** | svc-data, svc-position | 10992, 11999, 12999 |
| **svc-position** | svc-data | 10993 |

### Stub Publishing Services

The following services generate and publish contract stubs:

| Service | Stub Artifact | Local Path |
|---------|---------------|------------|
| **svc-data** | `org.beancounter:svc-data:0.1.1:stubs` | `~/.m2/repository/org/beancounter/svc-data/0.1.1/` |
| **svc-position** | `org.beancounter:svc-position:0.1.1:stubs` | `~/.m2/repository/org/beancounter/svc-position/0.1.1/` |

## Available Gradle Tasks

### Core Build Tasks

```bash
# Build all projects in dependency order
./gradlew buildAll

# Build core libraries only (jar-common, jar-auth, jar-client)
./gradlew buildCore

# Build all services after core libraries
./gradlew buildServices
```

### Stub Management Tasks

```bash
# Publish contract stubs to local Maven repository
./gradlew publishStubs

# Verify that stubs are available
./gradlew verifyStubs

# Complete build including stub publishing
./gradlew buildWithStubs
```

### Test Tasks

```bash
# Test all projects (automatically publishes stubs first)
./gradlew testAll

# Complete test run with stub verification
./gradlew testWithStubs

# Test individual services (stubs must be published first)
./gradlew :jar-client:test
./gradlew :jar-shell:test
./gradlew :svc-event:test
./gradlew :svc-position:test
```

### Utility Tasks

```bash
# Clean all projects
./gradlew cleanAll

# Validate project dependencies
./gradlew validateDependencies

# Verify all project dependencies
./gradlew verifyDependencies
```

## Day 0 Build Process

For a clean build from scratch (no existing stubs):

### ⚠️ Important: Clean Build Limitations

Due to circular dependencies, **clean builds will fail** with dependency resolution errors. This is expected behavior.

### Option 1: Manual Build Order (Recommended)

```bash
# Step 1: Build core libraries (no stub dependencies)
./gradlew :jar-common:build :jar-auth:build

# Step 2: Build services and publish stubs (in correct order)
./gradlew :svc-data:build
./gradlew :svc-data:pubStubs
./gradlew :svc-position:build
./gradlew :svc-position:pubStubs
./gradlew :svc-event:build

# Step 3: Build client libraries (now stubs are available)
./gradlew :jar-client:build :jar-shell:build
```

### Option 2: Use Build Script

```bash
# Use the provided build script
./build-with-stubs.sh
```

### Option 3: Incremental Development

```bash
# For daily development (when stubs exist)
./gradlew buildSmart    # Fast build - checks for stubs first
./gradlew testSmart     # Fast test - checks for stubs first
```

## Troubleshooting

### Common Issues

1. **Stub Not Found Errors**

   ```
   Could not find org.beancounter:svc-data:0.1.1:stubs
   ```

   **Solution**: Run the manual build order or use `./build-with-stubs.sh`

2. **Circular Dependency Errors**

   ```
   Could not resolve all files for configuration ':jar-client:testCompileClasspath'
   ```

   **Solution**: This is expected for clean builds. Use the manual build order.

3. **Port Already in Use**

   ```
   Address already in use
   ```

   **Solution**: Ensure no other tests are running, or use `./gradlew cleanAll`

### Verification Commands

```bash
# Check if stubs exist
./gradlew verifyStubs

# Check stub contents
ls -la ~/.m2/repository/org/beancounter/svc-data/0.1.1/
ls -la ~/.m2/repository/org/beancounter/svc-position/0.1.1/

# Validate dependencies
./gradlew validateDependencies
```

## CI/CD Considerations

### Build Pipeline

For CI/CD pipelines, use the manual build order:

```yaml
# Example GitHub Actions step
- name: Build and Test
  run: |
    ./gradlew :jar-common:build :jar-auth:build
    ./gradlew :svc-data:build
    ./gradlew :svc-data:pubStubs
    ./gradlew :svc-position:build
    ./gradlew :svc-position:pubStubs
    ./gradlew :svc-event:build
    ./gradlew :jar-client:build :jar-shell:build
    ./gradlew testAll
```

### Docker Builds

For Docker builds, ensure stubs are published before building images:

```dockerfile
# Example Dockerfile
FROM gradle:8.14-jdk21 AS builder
COPY . /app
WORKDIR /app
RUN ./gradlew :jar-common:build :jar-auth:build
RUN ./gradlew :svc-data:build
RUN ./gradlew :svc-data:pubStubs
RUN ./gradlew :svc-position:build
RUN ./gradlew :svc-position:pubStubs
RUN ./gradlew :svc-event:build
RUN ./gradlew :jar-client:build :jar-shell:build
```

## Performance Notes

- **Stub Publishing**: Takes ~30-60 seconds for both services
- **Test Execution**: ~15-38 seconds for all tests (with shared context)
- **Total Build Time**: ~2-3 minutes for complete build with tests

## Future Improvements

- Consider publishing stubs to a shared repository for team development
- Implement stub versioning for better dependency management
- Add stub validation to ensure contract compatibility
- **Consider restructuring** to eliminate circular dependencies
