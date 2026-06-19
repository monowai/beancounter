package com.beancounter.common.model

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDate

/**
 * Significant dates for a position.
 */
class DateValues {
    /**
     * The date of the very first transaction ever received for this asset.
     * Set once and never changes, even if position is sold out and re-entered.
     */
    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = "yyyy-MM-dd"
    )
    var firstTransaction: LocalDate? = null

    /**
     * The date the position went from nothing to something.
     * Set when the first transaction creates a position.
     */
    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = "yyyy-MM-dd"
    )
    var opened: LocalDate? = null

    /**
     * The date of the most recent transaction for this position.
     * Updated on every transaction accumulation.
     */
    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = "yyyy-MM-dd"
    )
    var last: LocalDate? = null

    /**
     * The date the position was closed (quantity reached zero).
     * Automatically set when a sell transaction brings the position quantity to zero.
     * Automatically cleared when a buy transaction reopens a closed position.
     */
    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = "yyyy-MM-dd"
    )
    var closed: LocalDate? = null

    /**
     * The date of the most recent dividend payment for this position.
     * Set automatically when dividend transactions are accumulated.
     */
    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = "yyyy-MM-dd"
    )
    var lastDividend: LocalDate? = null
}