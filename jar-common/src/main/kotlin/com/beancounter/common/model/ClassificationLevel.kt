package com.beancounter.common.model

/**
 * Classification levels within a standard.
 * Represents the hierarchy depth of a classification item.
 *
 * IMPORTANT: Enum ordinals are used for serialization.
 * New values MUST be added at the END to maintain backward compatibility.
 */
enum class ClassificationLevel {
    SECTOR,
    INDUSTRY
    // Future values (e.g., INDUSTRY_GROUP, SUB_INDUSTRY, DURATION, CREDIT_QUALITY)
    // MUST be added at the END of this enum
}