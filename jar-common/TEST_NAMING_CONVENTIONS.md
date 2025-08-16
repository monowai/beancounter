# Test Naming Conventions and Best Practices

## Overview

This document outlines the standardized naming conventions and best practices for tests in the `jar-common` module to ensure consistency, readability, and maintainability.

## Test Class Naming

### Convention: `{ClassName}Test`

- **Format**: `{ClassName}Test`
- **Examples**:
  - `MathUtilsTest`
  - `DateUtilsTest`
  - `PercentUtilsTest`
  - `CashUtilsTest`

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
fun `should calculate percentage correctly with valid values`()

@Test
fun `should return zero when values are null`()

@Test
fun `should serialize and deserialize currency correctly`()

@Test
fun `should throw BusinessException for invalid date strings`()

@Test
fun `should handle dates across different time zones correctly`()
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
 * Test suite for PercentUtils to ensure percentage calculations work correctly.
 * 
 * This class tests:
 * - Basic percentage calculations with valid values
 * - Null value handling (should return zero)
 * - Zero value handling (should return zero)
 * - Custom scale calculations
 * - Decimal value calculations
 * 
 * The PercentUtils.percent() method calculates the ratio of currentValue to oldValue
 * by dividing currentValue by oldValue with the specified scale.
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
fun `should reset monetary values to zero when resetCosts is called`() {
    // Given a MoneyValues instance with non-zero monetary values
    val currency = Currency("USD")
    val moneyValues = MoneyValues(currency).apply {
        averageCost = BigDecimal("10.00")
        costValue = BigDecimal("100.00")
        costBasis = BigDecimal("1000.00")
    }

    // When resetCosts method is called
    moneyValues.resetCosts()

    // Then all specified fields should be reset to BigDecimal.ZERO
    assertEquals(BigDecimal.ZERO, moneyValues.averageCost, "Average cost should be reset to zero")
    assertEquals(BigDecimal.ZERO, moneyValues.costValue, "Cost value should be reset to zero")
    assertEquals(BigDecimal.ZERO, moneyValues.costBasis, "Cost basis should be reset to zero")
}
```

## Test Organization

### Package Structure

```
src/test/kotlin/com/beancounter/common/
├── utils/
│   ├── MathUtilsTest.kt
│   ├── DateUtilsTest.kt
│   ├── PercentUtilsTest.kt
│   └── ...
├── model/
│   ├── CurrencyTest.kt
│   ├── AssetTest.kt
│   └── ...
└── contracts/
    ├── CurrencyResponseTest.kt
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
class MathUtilsTest {
    private val zeroDec = "0.00"
    private val ten = "10.00"
    private val oneThousandDec = "1000.00"
    
    // Test methods...
}
```

### Test Helpers

Use the `TestHelpers` object for common test data creation:

```kotlin
val asset = TestHelpers.createTestAsset("AAPL", "NASDAQ")
val currency = TestHelpers.createTestCurrency("USD")
```

## Code Quality Guidelines

### Do's

- ✅ Use descriptive test method names
- ✅ Document test classes with comprehensive KDoc
- ✅ Use consistent assertion patterns
- ✅ Group related tests together
- ✅ Use test helpers to reduce duplication
- ✅ Test both happy path and edge cases

### Don'ts

- ❌ Use abbreviated or unclear method names
- ❌ Skip documentation for test classes
- ❌ Mix different assertion libraries
- ❌ Create overly complex test methods
- ❌ Duplicate test setup code
- ❌ Test implementation details instead of behavior

## Migration Guide

### Old Pattern → New Pattern

```kotlin
// Old
@Test
fun is_MultiplySafe() { ... }

// New
@Test
fun `should handle multiplication safely with null and zero values`() { ... }
```

```kotlin
// Old
@Test
fun assetDefaults() { ... }

// New
@Test
fun `should create asset with correct defaults`() { ... }
```

```kotlin
// Old
@Test
fun is_CurrencySerializing() { ... }

// New
@Test
fun `should serialize and deserialize currency correctly`() { ... }
```

## Review Checklist

Before committing test changes, ensure:

- [ ] Test class follows naming convention `{ClassName}Test`
- [ ] Test methods use descriptive names with backticks
- [ ] Class has comprehensive KDoc documentation
- [ ] Test methods are focused and test single behavior
- [ ] Assertions use AssertJ consistently
- [ ] Test data is properly organized
- [ ] No code duplication (use TestHelpers where appropriate)
- [ ] All tests pass
- [ ] Edge cases are covered

## Examples of Good Test Names

### Utility Tests

- `should calculate percentage correctly with valid values`
- `should return zero when values are null`
- `should handle dates across different time zones correctly`
- `should generate correct key from asset object`

### Model Tests

- `should serialize and deserialize currency correctly`
- `should create asset with correct defaults`
- `should maintain currency pair consistency`
- `should honor currency contract`

### Exception Tests

- `should throw BusinessException for invalid date strings`
- `should throw BusinessException for non-date string input`
- `should handle null values safely`

### Integration Tests

- `should process valid dates without exceptions`
- `should match formatted date with local date for specific and today inputs`
- `should parse miscellaneous date formats correctly`
