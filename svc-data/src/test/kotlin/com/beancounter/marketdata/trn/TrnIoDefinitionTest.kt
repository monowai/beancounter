package com.beancounter.marketdata.trn

import com.beancounter.common.model.AccountingType
import com.beancounter.common.model.Asset
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Unit tests for TrnIoDefinition export and headers.
 */
class TrnIoDefinitionTest {
    private val dateUtils = DateUtils()
    private val trnIoDefinition = TrnIoDefinition(dateUtils)

    private val usd = Currency("USD")
    private val nzd = Currency("NZD")
    private val nasdaq = Market("NASDAQ")
    private val cashMarket = Market("CASH", "CASH")

    @Test
    fun headersMatchColumnEnum() {
        val headers = trnIoDefinition.headers()
        assertThat(headers).hasSize(TrnIoDefinition.Columns.entries.size)
        assertThat(headers[0]).isEqualTo("Batch")
        assertThat(headers[TrnIoDefinition.Columns.SubAccounts.ordinal]).isEqualTo("SubAccounts")
    }

    @Test
    fun exportBasicTransaction() {
        val asset =
            Asset(
                code = "AAPL",
                market = nasdaq,
                name = "Apple Inc."
            )
        val trn =
            Trn(
                trnType = TrnType.BUY,
                asset = asset,
                quantity = BigDecimal("10"),
                price = BigDecimal("150.50"),
                tradeAmount = BigDecimal("1505.00"),
                fees = BigDecimal("9.99"),
                tradeDate = LocalDate.of(2024, 6, 15),
                callerRef = CallerRef("BC", "BATCH1", "TRN001"),
                comments = "Test purchase",
                status = TrnStatus.SETTLED
            )

        val row = trnIoDefinition.export(trn)
        assertThat(row).hasSize(TrnIoDefinition.Columns.entries.size)
        assertThat(row[TrnIoDefinition.Columns.Batch.ordinal]).isEqualTo("BATCH1")
        assertThat(row[TrnIoDefinition.Columns.CallerId.ordinal]).isEqualTo("TRN001")
        assertThat(row[TrnIoDefinition.Columns.Type.ordinal]).isEqualTo("BUY")
        assertThat(row[TrnIoDefinition.Columns.Market.ordinal]).isEqualTo("NASDAQ")
        assertThat(row[TrnIoDefinition.Columns.Code.ordinal]).isEqualTo("AAPL")
        assertThat(row[TrnIoDefinition.Columns.Name.ordinal]).isEqualTo("Apple Inc.")
        assertThat(row[TrnIoDefinition.Columns.Date.ordinal]).isEqualTo("2024-06-15")
        assertThat(row[TrnIoDefinition.Columns.Quantity.ordinal]).isEqualTo("10")
        assertThat(row[TrnIoDefinition.Columns.Price.ordinal]).isEqualTo("150.50")
        assertThat(row[TrnIoDefinition.Columns.TradeAmount.ordinal]).isEqualTo("1505.00")
        assertThat(row[TrnIoDefinition.Columns.Fees.ordinal]).isEqualTo("9.99")
        assertThat(row[TrnIoDefinition.Columns.Comments.ordinal]).isEqualTo("Test purchase")
        assertThat(row[TrnIoDefinition.Columns.Status.ordinal]).isEqualTo("SETTLED")
    }

    @Test
    fun exportWithCashAssetUsingAccountingType() {
        val asset =
            Asset(
                code = "AAPL",
                market = nasdaq,
                name = "Apple Inc."
            )
        val cashAsset =
            Asset(
                code = "NZD",
                market = cashMarket,
                name = "NZD Balance",
                accountingType =
                    AccountingType(
                        id = "test-at",
                        category = "CASH",
                        currency = nzd
                    )
            )
        val trn =
            Trn(
                trnType = TrnType.BUY,
                asset = asset,
                cashAsset = cashAsset,
                quantity = BigDecimal("5"),
                tradeAmount = BigDecimal("750.00")
            )

        val row = trnIoDefinition.export(trn)
        assertThat(row[TrnIoDefinition.Columns.CashAccount.ordinal]).isEqualTo(cashAsset.id)
        assertThat(row[TrnIoDefinition.Columns.CashCurrency.ordinal]).isEqualTo("NZD")
    }

    @Test
    fun exportWithNullCashAsset() {
        val asset =
            Asset(
                code = "MSFT",
                market = nasdaq,
                name = "Microsoft"
            )
        val trn =
            Trn(
                trnType = TrnType.BUY,
                asset = asset,
                quantity = BigDecimal("1"),
                tradeAmount = BigDecimal("400.00")
            )

        val row = trnIoDefinition.export(trn)
        assertThat(row[TrnIoDefinition.Columns.CashAccount.ordinal]).isNull()
        assertThat(row[TrnIoDefinition.Columns.CashCurrency.ordinal]).isNull()
    }

    @Test
    fun exportWithCashAssetWithoutAccountingType() {
        val asset =
            Asset(
                code = "AAPL",
                market = nasdaq,
                name = "Apple Inc."
            )
        val cashAsset =
            Asset(
                code = "USD",
                market = cashMarket,
                name = "USD Balance"
            )
        val trn =
            Trn(
                trnType = TrnType.SELL,
                asset = asset,
                cashAsset = cashAsset,
                quantity = BigDecimal("3"),
                tradeAmount = BigDecimal("450.00")
            )

        val row = trnIoDefinition.export(trn)
        assertThat(row[TrnIoDefinition.Columns.CashAccount.ordinal]).isEqualTo(cashAsset.id)
        assertThat(row[TrnIoDefinition.Columns.CashCurrency.ordinal]).isNull()
    }

    @Test
    fun exportWithNullCallerRef() {
        val asset =
            Asset(
                code = "AMP",
                market = Market("ASX"),
                name = "AMP Ltd"
            )
        val trn =
            Trn(
                trnType = TrnType.DIVI,
                asset = asset,
                callerRef = null,
                quantity = BigDecimal.ZERO,
                tradeAmount = BigDecimal("25.00")
            )

        val row = trnIoDefinition.export(trn)
        assertThat(row[TrnIoDefinition.Columns.Batch.ordinal]).isNull()
        assertThat(row[TrnIoDefinition.Columns.CallerId.ordinal]).isNull()
        assertThat(row[TrnIoDefinition.Columns.Type.ordinal]).isEqualTo("DIVI")
    }

    @Test
    fun exportWithSubAccounts() {
        val asset =
            Asset(
                code = "AAPL",
                market = nasdaq,
                name = "Apple Inc."
            )
        val subAccounts = mapOf("sub1" to BigDecimal("60"), "sub2" to BigDecimal("40"))
        val trn =
            Trn(
                trnType = TrnType.BUY,
                asset = asset,
                quantity = BigDecimal("10"),
                tradeAmount = BigDecimal("1500.00"),
                subAccounts = subAccounts
            )

        val row = trnIoDefinition.export(trn)
        val subAccountsJson = row[TrnIoDefinition.Columns.SubAccounts.ordinal]
        assertThat(subAccountsJson).isNotNull
        assertThat(subAccountsJson).contains("sub1")
        assertThat(subAccountsJson).contains("sub2")
    }

    @Test
    fun exportWithNullSubAccounts() {
        val asset =
            Asset(
                code = "AAPL",
                market = nasdaq,
                name = "Apple Inc."
            )
        val trn =
            Trn(
                trnType = TrnType.BUY,
                asset = asset,
                quantity = BigDecimal("1"),
                tradeAmount = BigDecimal("150.00"),
                subAccounts = null
            )

        val row = trnIoDefinition.export(trn)
        assertThat(row[TrnIoDefinition.Columns.SubAccounts.ordinal]).isNull()
    }
}