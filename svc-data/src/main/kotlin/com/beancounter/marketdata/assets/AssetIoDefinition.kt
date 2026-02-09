package com.beancounter.marketdata.assets

import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import org.springframework.stereotype.Service

/**
 * Export and Import format for PRIVATE market assets.
 * Handles CSV serialization/deserialization for user-owned assets.
 */
@Service
class AssetIoDefinition {
    /**
     * CSV columns for asset export/import.
     */
    enum class Columns {
        Code,
        Name,
        Category,
        Currency
    }

    /**
     * Returns the header row for CSV export.
     */
    fun headers(): Array<String> =
        Columns.entries
            .map(Enum<*>::name)
            .toTypedArray()

    /**
     * Exports an asset to a CSV row.
     * Strips the user prefix from the code for cleaner export.
     */
    fun export(asset: Asset): Array<String?> {
        val displayCode = getDisplayCode(asset.code)
        return arrayOf(
            displayCode,
            asset.name,
            asset.assetCategory.id,
            asset.accountingType?.currency?.code ?: asset.market.currency.code
        )
    }

    /**
     * Parses a CSV row into an AssetInput for import.
     * @param row Array of column values in order: Code, Name, Category, Currency
     * @param owner The owner ID to associate with the asset
     * @return AssetInput ready for creation
     */
    fun parse(
        row: Array<String>,
        owner: String
    ): AssetInput {
        require(row.size >= Columns.entries.size) {
            "Invalid row: expected ${Columns.entries.size} columns, got ${row.size}"
        }
        return AssetInput(
            market = PrivateMarketEnricher.ID,
            code = row[Columns.Code.ordinal].trim(),
            name = row[Columns.Name.ordinal].trim().ifEmpty { null },
            currency = row[Columns.Currency.ordinal].trim().ifEmpty { null },
            category = row[Columns.Category.ordinal].trim().uppercase().ifEmpty { "ACCOUNT" },
            owner = owner
        )
    }

    /**
     * Extract the display code from a full asset code, stripping the owner prefix.
     * e.g., "userId.WISE" -> "WISE"
     */
    private fun getDisplayCode(code: String): String {
        val dotIndex = code.lastIndexOf(".")
        return if (dotIndex >= 0) code.substring(dotIndex + 1) else code
    }
}