package com.beancounter.marketdata.portfolio

import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.Portfolio
import org.springframework.stereotype.Service

/**
 * Export and Import format for portfolios.
 * Handles CSV serialization/deserialization for user portfolios.
 */
@Service
class PortfolioIoDefinition {
    /**
     * CSV columns for portfolio export/import.
     */
    enum class Columns {
        Code,
        Name,
        Currency,
        Base
    }

    /**
     * Returns the header row for CSV export.
     */
    fun headers(): Array<String> =
        Columns.entries
            .map(Enum<*>::name)
            .toTypedArray()

    /**
     * Exports a portfolio to a CSV row.
     */
    fun export(portfolio: Portfolio): Array<String?> =
        arrayOf(
            portfolio.code,
            portfolio.name,
            portfolio.currency.code,
            portfolio.base.code
        )

    /**
     * Parses a CSV row into a PortfolioInput for import.
     * @param row Array of column values in order: Code, Name, Currency, Base
     * @return PortfolioInput ready for creation
     */
    fun parse(row: Array<String>): PortfolioInput {
        require(row.size >= Columns.entries.size) {
            "Invalid row: expected ${Columns.entries.size} columns, got ${row.size}"
        }
        return PortfolioInput(
            code = row[Columns.Code.ordinal].trim(),
            name = row[Columns.Name.ordinal].trim().ifEmpty { row[Columns.Code.ordinal].trim() },
            currency = row[Columns.Currency.ordinal].trim().ifEmpty { "USD" },
            base = row[Columns.Base.ordinal].trim().ifEmpty { row[Columns.Currency.ordinal].trim() }
        )
    }

    /**
     * Check if the row is a header row.
     */
    fun isHeaderRow(row: Array<String>): Boolean =
        row.isNotEmpty() &&
            row[0].equals(Columns.Code.name, ignoreCase = true)
}