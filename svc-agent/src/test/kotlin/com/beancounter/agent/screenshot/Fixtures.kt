package com.beancounter.agent.screenshot

import com.beancounter.common.utils.BcJson
import org.assertj.core.api.Assertions.assertThat
import java.math.BigDecimal

/** The fixture screenshots under `src/test/resources/extract`, by file name without extension. */
val FIXTURES =
    listOf(
        "schwab-web-positions",
        "etrade-web-positions",
        "mobile-dark-no-broker",
        "schwab-mobile-roth",
        "etrade-web-transactions",
        "schwab-web-dense"
    )

/**
 * What a fixture screenshot must yield. A field left out of a row is not checked.
 * [unsupported] is the asset type of each row that is neither a listed holding nor cash.
 */
data class Expected(
    val documentType: DocumentType,
    val brokerName: String?,
    val accountNumber: String?,
    val totalShown: BigDecimal? = null,
    val rows: List<ProposalRow>,
    val unsupported: List<String> = emptyList()
)

fun fixtureBytes(file: String): ByteArray =
    checkNotNull(Expected::class.java.getResourceAsStream("/extract/$file")) { "No fixture $file" }
        .use { it.readBytes() }

fun expected(name: String): Expected =
    BcJson.objectMapper.readValue(fixtureBytes("$name.expected.json"), Expected::class.java)

private val LISTED = setOf("EQUITY", "ETF")

/** Fails unless [proposal] carries everything [expected] names, row for row and in order. */
fun assertMatches(
    proposal: ScreenshotProposal,
    expected: Expected
) {
    assertThat(proposal.documentType).isEqualTo(expected.documentType)
    assertThat(proposal.brokerName).isEqualTo(expected.brokerName)
    assertThat(proposal.accountNumber).isEqualTo(expected.accountNumber)
    expected.totalShown?.let { assertThat(proposal.totalShown).isEqualByComparingTo(it) }
    val holdings = expected.documentType == DocumentType.HOLDINGS
    val rows = if (holdings) proposal.rows.filter { it.assetType in LISTED } else proposal.rows
    assertThat(rows).hasSameSizeAs(expected.rows)
    rows.zip(expected.rows).forEach { (got, want) -> assertRow(got, want) }
    if (holdings) {
        assertThat(proposal.rows.filterNot { it.assetType in LISTED || it.assetType == "CASH" }.map { it.assetType })
            .containsExactlyElementsOf(expected.unsupported)
    }
}

private fun assertRow(
    got: ProposalRow,
    want: ProposalRow
) {
    val label = "row ${want.code ?: want.type} ${want.tradeDate ?: ""}"
    assertThat(got.code).`as`(label).isEqualTo(want.code)
    assertThat(got.tradeDate).`as`(label).isEqualTo(want.tradeDate)
    want.type?.let { assertThat(got.type).`as`(label).isEqualTo(it) }
    listOf(
        "quantity" to (got.quantity to want.quantity),
        "price" to (got.price to want.price),
        "marketValue" to (got.marketValue to want.marketValue),
        "costBasis" to (got.costBasis to want.costBasis),
        "costPerShare" to (got.costPerShare to want.costPerShare),
        "gainLoss" to (got.gainLoss to want.gainLoss),
        "amount" to (got.amount to want.amount),
        "fees" to (got.fees to want.fees)
    ).forEach { (field, pair) ->
        pair.second?.let { assertThat(pair.first).`as`("$label $field").isEqualByComparingTo(it) }
    }
    if (want.costBasis == null && want.costPerShare == null && want.amount == null) {
        assertThat(listOf(got.costBasis, got.costPerShare)).`as`("$label invented cost").containsOnlyNulls()
    }
}