package com.beancounter.client.ingest

import com.beancounter.client.FxService
import com.beancounter.common.Constants
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Currency
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils
import com.beancounter.common.utils.DateUtils
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Settling a PROPOSED trn resolves its deferred FX rates. When the provider cannot supply
 * one of the pairs, the caller is told which pair is missing as a business error (a client
 * error), the same way the import path reports it, rather than as an unexpected server error.
 */
class FxTransactionsSettleMissingRateTest {
    private val usd = Currency("USD")
    private val nzd = Currency("NZD")
    private val gbp = Currency("GBP")

    // Supplies the portfolio pair only; the cash pair is unavailable.
    private val partialFxService =
        object : FxService {
            override fun getRates(
                fxRequest: FxRequest,
                token: String
            ): FxResponse =
                FxResponse(
                    FxPairResults(
                        mapOf(
                            IsoCurrencyPair(usd.code, nzd.code) to
                                FxRate(from = usd, to = nzd, rate = BigDecimal("1.50"))
                        )
                    )
                )
        }

    private val fxTransactions = FxTransactions(partialFxService)

    private val portfolio =
        Portfolio(
            id = "settle-fx",
            currency = nzd,
            base = usd
        )

    private val trn =
        Trn(
            trnType = TrnType.DIVI,
            tradeDate = DateUtils().date,
            asset = AssetUtils.getTestAsset(Constants.NYSE, "settle-fx-asset"),
            tradeCurrency = usd,
            cashCurrency = gbp,
            tradeCashRate = BigDecimal.ZERO,
            tradeBaseRate = BigDecimal.ZERO,
            tradePortfolioRate = BigDecimal.ZERO,
            portfolio = portfolio
        )

    @Test
    fun `should report a missing settle-time fx rate as a business error`() {
        assertThatThrownBy { fxTransactions.setRates(portfolio, trn) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("Missing FX rate for USD:GBP")
    }

    @Test
    fun `should leave the trn rates unchanged when a settle-time fx rate is missing`() {
        runCatching { fxTransactions.setRates(portfolio, trn) }

        assertThat(trn.tradePortfolioRate).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(trn.tradeBaseRate).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(trn.tradeCashRate).isEqualByComparingTo(BigDecimal.ZERO)
    }
}