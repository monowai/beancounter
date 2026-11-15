package com.beancounter.agent.screenshot

import tools.jackson.databind.annotation.JsonDeserialize
import java.math.BigDecimal
import java.time.LocalDate

/** What kind of brokerage page a screenshot shows. */
enum class DocumentType {
    /** What is held now: one row per asset. */
    HOLDINGS,

    /** What happened: one row per transaction. */
    TRANSACTIONS
}

/**
 * What a brokerage screenshot shows, as read by a vision model. A proposal: nothing
 * here is checked against Beancounter's data, and the caller's user confirms it
 * before svc-data records anything. A null field was not on the screenshot.
 *
 * @property brokerName the firm's name as printed
 * @property accountNumber the digits of the account number that are visible; brokers mask the rest
 * @property totalShown the account or list total printed on the page
 */
data class ScreenshotProposal(
    val documentType: DocumentType,
    val brokerName: String? = null,
    val accountNumber: String? = null,
    val asOf: String? = null,
    @param:JsonDeserialize(using = PrintedNumber::class)
    val totalShown: BigDecimal? = null,
    val rows: List<ProposalRow> = emptyList()
)

/**
 * One holding or one transaction. Numbers are as displayed, never computed.
 *
 * @property code the ticker symbol as printed
 * @property assetType EQUITY, ETF, CRYPTO, OPTION, CASH or OTHER
 * @property costBasis total cost of a holding
 * @property costPerShare average cost, or price paid, for one share
 * @property type a transaction's type as printed, for example "Bought"
 * @property amount a transaction's cash amount, negative when cash left the account
 */
data class ProposalRow(
    val code: String? = null,
    val name: String? = null,
    val assetType: String? = null,
    @param:JsonDeserialize(using = PrintedNumber::class)
    val quantity: BigDecimal? = null,
    @param:JsonDeserialize(using = PrintedNumber::class)
    val price: BigDecimal? = null,
    @param:JsonDeserialize(using = PrintedNumber::class)
    val marketValue: BigDecimal? = null,
    @param:JsonDeserialize(using = PrintedNumber::class)
    val costBasis: BigDecimal? = null,
    @param:JsonDeserialize(using = PrintedNumber::class)
    val costPerShare: BigDecimal? = null,
    @param:JsonDeserialize(using = PrintedNumber::class)
    val gainLoss: BigDecimal? = null,
    val currency: String? = null,
    val tradeDate: LocalDate? = null,
    val type: String? = null,
    @param:JsonDeserialize(using = PrintedNumber::class)
    val amount: BigDecimal? = null,
    @param:JsonDeserialize(using = PrintedNumber::class)
    val fees: BigDecimal? = null
)