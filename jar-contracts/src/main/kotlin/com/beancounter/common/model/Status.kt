package com.beancounter.common.model

/**
 * General status values tracked by BC. Stored as STRING in the database via @Enumerated(EnumType.STRING).
 *
 * @author mikeh
 * @since 2021-11-05
 */
enum class Status {
    Active,
    Inactive
}