# Test Naming Conventions and Best Practices for svc-data

## Overview

This document outlines the standardized naming conventions and best practices for tests in the `svc-data` module to ensure consistency, readability, and maintainability. These standards align with the conventions established in `jar-common` and `jar-auth`.

## Test Class Naming

### Convention: `{ClassName}Test`

- **Format**: `{ClassName}Test`
- **Examples**:
  - `TrnControllerTest`
  - `AssetControllerTest`
  - `EnrichmentTest`
  - `PortfolioServiceTest`

### Guidelines

- Use the exact class name being tested + "Test"
- Keep the same package structure as the class being tested
- Use `internal` visibility modifier for test classes

## Test Method Naming

### Convention: `should {expected behavior} when {condition}`

- **Format**: Use backticks with descriptive names
- **Pattern**: `should {action/result} when {condition/input}`

### Examples

```kotlin
@Test
fun `should create transaction successfully when valid input is provided`()

@Test
fun `should return unauthorized when no token is provided`()

@Test
fun `should enrich asset with FIGI data when asset name is null`()

@Test
fun `should throw BusinessException for invalid transaction ID`()

@Test
fun `should handle portfolio deletion with cascading transactions`()
```

### Guidelines

- Start with "should" to indicate expected behavior
- Use present tense for actions
- Be specific about the condition or input
- Use backticks for multi-word method names
- Avoid abbreviations unless they are widely understood

## Test Class Documentation

### Required KDoc Structure

```kotlin
/**
 * Test suite for {ClassName} to ensure {primary functionality}.
 * 
 * This class tests:
 * - {specific functionality 1}
 * - {specific functionality 2}
 * - {specific functionality 3}
 * - {edge cases or special scenarios}
 * 
 * {Additional context if needed}
 */
```

### Examples

```kotlin
/**
 * Test suite for TrnController to ensure transaction lifecycle management works correctly.
 * 
 * This class tests:
 * - Transaction creation with valid input data
 * - Transaction retrieval by various criteria (ID, portfolio, asset)
 * - Transaction deletion (single and bulk operations)
 * - Error handling for invalid inputs
 * - Authorization and security validation
 * 
 * Tests use Spring Boot Test with MockMvc to simulate HTTP requests
 * and verify the complete transaction workflow.
 */
```

## Test Method Documentation

### Guidelines

- Use inline comments for complex test logic
- Document the "Given-When-Then" structure for complex tests
- Explain the business logic being tested
- Document edge cases and their expected behavior

### Example

```kotlin
@Test
fun `should create and retrieve transaction successfully when valid portfolio and asset exist`() {
    // Given a valid portfolio and asset in the system
    val portfolio = createTestPortfolio("Test Portfolio", "USD")
    val asset = createTestAsset("AAPL", "NASDAQ")
    
    // And a valid transaction input
    val trnInput = TrnInput(
        callerRef = CallerRef("test-caller"),
        assetId = asset.id,
        trnType = TrnType.BUY,
        quantity = BigDecimal.TEN,
        price = BigDecimal("150.00"),
        tradeDate = dateUtils.today(),
        tradeCurrency = "USD"
    )
    
    // When the transaction is created
    val trnRequest = TrnRequest(portfolio.id, arrayOf(trnInput))
    val response = bcMvcHelper.postTrn(trnRequest)
    
    // Then the transaction should be created successfully
    assertThat(response.status).isEqualTo(200)
    assertThat(response.data).hasSize(1)
    
    // And the transaction should be retrievable by ID
    val retrievedTrn = retrieveTransactionById(response.data.first().id)
    assertThat(retrievedTrn).isNotNull
    assertThat(retrievedTrn.assetId).isEqualTo(asset.id)
}
```

## Test Organization

### Package Structure

```
src/test/kotlin/com/beancounter/marketdata/
├── trn/
│   ├── TrnControllerTest.kt
│   ├── TrnControllerFlowTest.kt
│   ├── TrnAdapterTest.kt
│   └── ...
├── assets/
│   ├── AssetControllerTest.kt
│   ├── EnrichmentTest.kt
│   └── ...
├── portfolio/
│   ├── PortfolioServiceTest.kt
│   └── ...
└── utils/
    ├── TestHelpers.kt
    └── ...
```

### Test Class Structure

1. **Class-level documentation** (KDoc)
2. **Test fixtures and setup** (private properties, @BeforeEach)
3. **Test methods** (ordered by complexity: simple → complex)
4. **Helper methods** (private methods for test utilities)

## Assertion Patterns

### Preferred Assertion Library: AssertJ

```kotlin
// Good
assertThat(result).isEqualTo(expectedValue)
assertThat(collection).hasSize(3)
assertThat(exception).hasMessage("Expected error message")

// Avoid
assertEquals(expectedValue, result)
assertTrue(condition)
```

### Exception Testing

```kotlin
@Test
fun `should throw BusinessException for invalid input`() {
    assertThrows(BusinessException::class.java) {
        // code that should throw exception
    }
}
```

## Test Data Management

### Test Constants

```kotlin
class TrnControllerTest {
    private val testTradeDate = "2024-01-15"
    private val testQuantity = BigDecimal.TEN
    private val testPrice = BigDecimal("150.00")
    
    // Test methods...
}
```

### Test Helpers

Use the `TestHelpers` object for common test data creation:

```kotlin
val asset = TestHelpers.createTestAsset("AAPL", "NASDAQ")
val portfolio = TestHelpers.createTestPortfolio("Test Portfolio", "USD")
val trnInput = TestHelpers.createTestTrnInput(asset.id, TrnType.BUY)
```

## Integration Test Patterns

### Spring Boot Test Annotations

```kotlin
@SpringMvcDbTest
class TrnControllerTest {
    // Database integration tests
}

@SpringMvcKafkaTest
class TrnEventTest {
    // Kafka integration tests
}
```

### MockMvc Helper Usage

```kotlin
@BeforeEach
fun configure() {
    token = mockAuthConfig.getUserToken(Constants.systemUser)
    bcMvcHelper = BcMvcHelper(mockMvc, token)
    bcMvcHelper.registerUser()
}
```

## Code Quality Guidelines

### Do's

- ✅ Use descriptive test method names with backticks
- ✅ Document test classes with comprehensive KDoc
- ✅ Use consistent assertion patterns with AssertJ
- ✅ Group related tests together
- ✅ Use test helpers to reduce duplication
- ✅ Test both happy path and edge cases
- ✅ Use meaningful test data constants
- ✅ Follow Given-When-Then structure for complex tests

### Don'ts

- ❌ Use abbreviated or unclear method names (e.g., `is_FigiEnrichment()`)
- ❌ Skip documentation for test classes
- ❌ Mix different assertion libraries
- ❌ Create overly complex test methods
- ❌ Duplicate test setup code
- ❌ Test implementation details instead of behavior
- ❌ Use hardcoded values without constants

## Migration Guide

### Old Pattern → New Pattern

```kotlin
// Old
@Test
fun is_FigiEnrichment() { ... }

// New
@Test
fun `should enrich asset with FIGI data when asset name is null`() { ... }
```

```kotlin
// Old
@Test
fun nonExistentThrowsException() { ... }

// New
@Test
fun `should throw BusinessException when transaction ID does not exist`() { ... }
```

```kotlin
// Old
@Test
fun is_PersistRetrieveAndPurge() { ... }

// New
@Test
fun `should persist retrieve and delete transactions in complete lifecycle`() { ... }
```

## Review Checklist

Before committing test changes, ensure:

- [ ] Test class follows naming convention `{ClassName}Test`
- [ ] Test methods use descriptive names with backticks
- [ ] Class has comprehensive KDoc documentation
- [ ] Test methods are focused and test single behavior
- [ ] Assertions use AssertJ consistently
- [ ] Test data is properly organized with constants
- [ ] No code duplication (use TestHelpers where appropriate)
- [ ] All tests pass
- [ ] Edge cases are covered
- [ ] Integration tests use appropriate annotations
- [ ] MockMvc tests follow established patterns

## Examples of Good Test Names

### Controller Tests

- `should create transaction successfully when valid input is provided`
- `should return unauthorized when no token is provided`
- `should return bad request when invalid transaction ID is provided`
- `should delete transaction successfully when valid ID is provided`

### Service Tests

- `should enrich asset with FIGI data when asset name is null`
- `should skip enrichment when asset name is already populated`
- `should calculate portfolio value correctly with multiple assets`
- `should handle currency conversion for foreign assets`

### Integration Tests

- `should process transaction through complete lifecycle`
- `should handle concurrent transaction creation`
- `should maintain data consistency across database operations`
- `should propagate events to Kafka when transaction is created`

### Exception Tests

- `should throw BusinessException for invalid transaction ID`
- `should throw BusinessException when portfolio does not exist`
- `should handle null values safely in transaction processing`
- `should validate required fields before processing`
