package com.beancounter.client.ingest

import com.beancounter.client.FxService
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.exception.SystemException
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * A trade whose currency matches both the portfolio and its base needs no conversion —
 * every pair resolves to null, so the request carries no pairs at all. Asking the FX
 * service for nothing is not merely wasteful: the rate lookup is date-driven, so it
 * fails whenever no rate happens to be cached for that date, and a trade that needed
 * no FX is rejected because of FX.
 */
class FxTransactionsSameCurrencyTest {
    private val usd = Currency("USD")

    /**
     * Stands in for a server that has no rate cached for the trade date — the state that
     * makes this fail. Any call at all is the defect, so the fake refuses every one.
     */
    private val unreachableFxService =
        object : FxService {
            override fun getRates(
                fxRequest: FxRequest,
                token: String
            ): FxResponse = throw SystemException("No rates found from EXCHANGE_RATES_API")
        }

    private val fxTransactions = FxTransactions(unreachableFxService)

    private val portfolio =
        Portfolio(
            id = "same-ccy",
            currency = usd,
            base = usd
        )

    private val trnInput =
        TrnInput(
            callerRef = CallerRef(),
            assetId = "asset-id",
            trnType = TrnType.BUY,
            quantity = BigDecimal("4"),
            tradeCurrency = usd.code,
            tradeDate = DateUtils().date,
            price = BigDecimal("500.00"),
            tradeAmount = BigDecimal("2000.00")
        )

    @Test
    fun `should not consult the fx service when no currency pair needs converting`() {
        fxTransactions.setRates(
            portfolio,
            trnInput
        )

        assertThat(trnInput.tradePortfolioRate).isEqualByComparingTo(BigDecimal.ONE)
        assertThat(trnInput.tradeBaseRate).isEqualByComparingTo(BigDecimal.ONE)
        assertThat(trnInput.tradeCashRate).isEqualByComparingTo(BigDecimal.ONE)
    }
}