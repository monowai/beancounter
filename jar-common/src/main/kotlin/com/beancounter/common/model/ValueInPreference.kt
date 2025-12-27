package com.beancounter.common.model

/**
 * Available value-in options for displaying holdings values.
 * Determines which currency perspective to use when showing values.
 */
enum class ValueInPreference {
    PORTFOLIO,
    BASE,
    TRADE
}