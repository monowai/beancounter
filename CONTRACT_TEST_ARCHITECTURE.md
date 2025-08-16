# Contract Test Architecture

## Overview

This project uses a **hybrid approach** for contract testing that balances performance with reliability:

1. **Shared Context Approach** (default) - For most tests
2. **Isolated Context Approach** (special cases) - For complex tests like Kafka

## Architecture

### Shared Context Approach (Primary)

**Benefits:**
- ~4-6x faster test execution
- Reduced memory usage
- Faster CI/CD pipelines

**Configuration:**
- Base config: `jar-common/src/test/resources/application-contract-base.yml`
- Service-specific configs: `application-{service}-shared.yml`
- Profile: `@ActiveProfiles("{service}-shared", "contract-base")`
- Context reuse: `@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)`

**Port Allocation:**
- jar-client: 10990
- jar-shell: 10991
- svc-event: 10992
- svc-position: 10993

### Isolated Context Approach (Special Cases)

**When to use:**
- Kafka tests (complex consumer/producer setup)
- Tests requiring complete isolation
- Tests with timing-sensitive operations

**Configuration:**
- Dedicated config: `application-kafka.yaml`
- Profile: `@ActiveProfiles("kafka")`
- Context isolation: `@DirtiesContext`
- Fixed ports: 11999, 12999

## Implementation Details

### Base Configuration (`application-contract-base.yml`)

Contains common settings shared across all contract tests:
- Exchange aliases
- Market data provider settings
- Server configuration
- Rates and amount settings

### Service-Specific Configurations

Each service has a minimal configuration file that extends the base:
```yaml
# Only service-specific overrides
marketdata:
  url: http://localhost:{port}
```

### Test Class Pattern

**Shared Context Tests:**
```kotlin
@ActiveProfiles("service-shared", "contract-base")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureStubRunner(
    ids = ["org.beancounter:svc-data:0.1.1:stubs:{port}"]
)
```

**Isolated Context Tests:**
```kotlin
@ActiveProfiles("kafka")
@DirtiesContext
@AutoConfigureStubRunner(
    ids = ["org.beancounter:svc-data:0.1.1:stubs:11999"]
)
```

## Performance Results

| Approach | Test Classes | Execution Time | Performance Gain |
|----------|-------------|----------------|------------------|
| **Shared Context** | 30+ classes | ~15-38 seconds | **~4-6x faster** |
| **Isolated Context** | 2 Kafka classes | ~24 seconds | Baseline |

## Maintenance

### Adding a New Service

1. Create `application-{new-service}-shared.yml` with only service-specific settings
2. Add `@ActiveProfiles("{new-service}-shared", "contract-base")` to test classes
3. Assign a unique port from the range 10990-10999

### Adding Complex Tests

1. Create dedicated configuration file (e.g., `application-complex.yaml`)
2. Use `@ActiveProfiles("complex")` and `@DirtiesContext`
3. Assign ports from range 11999-12999

### Modifying Common Settings

Edit only `application-contract-base.yml` - changes apply to all shared context tests.

## Troubleshooting

### Common Issues

1. **Port Conflicts**: Ensure each service uses unique ports
2. **Kafka Timing Issues**: Use isolated context approach
3. **Memory Issues**: Increase JVM heap size in `gradle.properties`

### Debugging

- Run individual test classes: `./gradlew :service:test --tests "*TestClass*"`
- Check stub runner logs for port binding issues
- Verify configuration profiles are correctly applied

## Future Considerations

- Monitor test execution times to identify candidates for shared context
- Consider parallel execution for isolated context tests
- Evaluate new Spring Boot versions for improved context sharing capabilities
