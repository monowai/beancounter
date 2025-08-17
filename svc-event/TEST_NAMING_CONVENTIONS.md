# Test Naming Conventions for svc-event

This document outlines the test naming conventions and best practices for the `svc-event` service, aligning with the standards established in `jar-common`, `jar-auth`, and `svc-position`.

## Test Class Naming

- **Format**: `{ClassName}Test`
- **Example**: `EventServiceTest`, `EventControllerTest`, `EventLoaderTest`

## Test Method Naming

- **Format**: `should {expected behavior} when {condition}`
- **Examples**:
  - `should process corporate events when valid event data is provided`
  - `should handle empty event list gracefully when no events exist`
  - `should schedule events correctly when valid schedule is provided`
  - `should handle event errors gracefully when processing fails`

## KDoc Documentation

### Class-Level Documentation

```kotlin
/**
 * Test suite for {ClassName} to ensure proper {functionality}.
 * 
 * This class tests:
 * - {specific functionality 1}
 * - {specific functionality 2}
 * - {specific functionality 3}
 * 
 * Tests verify that the {ClassName} correctly {expected behavior}
 * when {various conditions}.
 */
```

### Method-Level Documentation

```kotlin
/**
 * Tests that {specific behavior} occurs when {specific condition}.
 * 
 * This test verifies:
 * - {verification point 1}
 * - {verification point 2}
 * - {verification point 3}
 */
```

## Test Structure

### Given-When-Then Pattern

```kotlin
@Test
fun `should {expected behavior} when {condition}`() {
    // Given {setup/arrange}
    val testData = createTestData()
    
    // When {action}
    val result = serviceUnderTest.method(testData)
    
    // Then {assertions}
    assertThat(result).isNotNull()
    assertThat(result.property).isEqualTo(expectedValue)
}
```

## Assertions

- Use **AssertJ** for all assertions
- Prefer descriptive assertion methods
- Use `assertThat()` consistently

```kotlin
// Good
assertThat(result).isNotNull()
assertThat(result.events).hasSize(2)
assertThat(result.status).isEqualTo(EventStatus.PROCESSED)

// Avoid
assertNotNull(result)
assertEquals(2, result.events.size)
```

## Test Data Management

### Constants

- Define test constants in a dedicated `Constants.kt` file
- Use descriptive names for test data
- Group related constants together

```kotlin
object TestConstants {
    const val TEST_EVENT_ID = "test-event-123"
    const val TEST_ASSET_CODE = "AAPL"
    const val TEST_PORTFOLIO_ID = "test-portfolio"
    const val TEST_EVENT_TYPE = "DIVIDEND"
}
```

### Test Helpers

- Create helper methods for common test data creation
- Use builder patterns for complex objects
- Centralize test utilities in `TestHelpers.kt`

```kotlin
object TestHelpers {
    fun createTestEvent(id: String = "test-event"): Event {
        return Event(
            id = id,
            type = EventType.DIVIDEND,
            assetId = "AAPL"
        )
    }
    
    fun createTestCorporateEvent(assetCode: String, eventType: String): CorporateEvent {
        return CorporateEvent(
            assetId = assetCode,
            trnType = TrnType.DIVI,
            rate = BigDecimal("0.50")
        )
    }
}
```

## Mocking Guidelines

- Use `@Mock` annotation for dependencies
- Use `@ExtendWith(MockitoExtension::class)` for unit tests
- Use `whenever().thenReturn()` for mock setup
- Use `verify()` for interaction verification

```kotlin
@ExtendWith(MockitoExtension::class)
class ServiceTest {
    @Mock
    private lateinit var dependency: Dependency
    
    @Test
    fun `should call dependency when processing event`() {
        // Given
        whenever(dependency.method(any())).thenReturn(expectedResult)
        
        // When
        serviceUnderTest.process(event)
        
        // Then
        verify(dependency).method(event)
    }
}
```

## Integration Tests

- Use `@SpringBootTest` for integration tests
- Use `@AutoConfigureMockMvc` for web layer tests
- Use `@TestPropertySource` for test-specific properties

```kotlin
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["test.property=value"])
class IntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc
    
    @Test
    fun `should return events when valid request is made`() {
        // Given
        val request = createValidRequest()
        
        // When & Then
        mockMvc.perform(post("/events")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.events").exists())
    }
}
```

## Code Coverage

- Aim for high code coverage (80%+)
- Focus on critical business logic paths
- Test edge cases and error conditions
- Use `@Test` annotations for all test methods

## Best Practices

1. **Single Responsibility**: Each test should verify one specific behavior
2. **Descriptive Names**: Test names should clearly describe what is being tested
3. **Independent Tests**: Tests should not depend on each other
4. **Fast Execution**: Tests should run quickly and not require external services
5. **Maintainable**: Tests should be easy to understand and modify
6. **Consistent Structure**: Follow the same pattern across all test files

## Migration Guidelines

When updating existing tests:

1. Rename test methods to follow the new convention
2. Add comprehensive KDoc documentation
3. Implement Given-When-Then structure
4. Use AssertJ assertions consistently
5. Extract common test data to helpers
6. Ensure all tests follow the established patterns

## Benefits

- **Improved Readability**: Clear test names and structure make tests easier to understand
- **Better Maintenance**: Consistent patterns reduce the effort needed to update tests
- **Enhanced Collaboration**: Standardized approach helps team members work together
- **Reduced Duplication**: Shared test helpers eliminate code duplication
- **Higher Quality**: Comprehensive documentation and structure lead to better test coverage
