package com.beancounter.common.model

import java.time.LocalDate

/**
 * Represents a cash flow at a specific period.
 */
data class CashFlow(val amount: Double, val date: LocalDate)
