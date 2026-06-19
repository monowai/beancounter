package com.beancounter.common.trn

import com.beancounter.common.Constants
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.PortfolioUtils
import tools.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Wire-payload normalization: TrnResponse carries a TrnPayload envelope
 * where Asset / Portfolio / Currency objects are de-duplicated into
 * top-level maps and each Trn references them by id / code.
 */
class TrnPayloadTest {
    private val portfolio = PortfolioUtils.getPortfolio()
    private val assetA =
        AssetUtils.getTestAsset(
            Constants.NYSE,
            "AAPL"
        )
    private val assetB =
        AssetUtils.getTestAsset(
            Constants.NYSE,
            "MSFT"
        )

    private fun buy(
        id: String,
        asset: com.beancounter.common.model.Asset
    ): Trn =
        Trn(
            id = id,
            trnType = TrnType.BUY,
            asset = asset,
            portfolio = portfolio,
            quantity = BigDecimal("10"),
            price = BigDecimal("100"),
            tradeAmount = BigDecimal("1000")
        )

    @Test
    fun `TrnResponse envelope de-dupes asset and portfolio`() {
        val response =
            TrnResponse(
                listOf(
                    buy("t1", assetA),
                    buy("t2", assetA),
                    buy("t3", assetB)
                )
            )

        val payload = response.data
        assertThat(payload.trns).hasSize(3)
        assertThat(payload.assets).containsKeys(assetA.id, assetB.id)
        assertThat(payload.assets).hasSize(2)
        assertThat(payload.portfolios).containsKey(portfolio.id).hasSize(1)
        assertThat(payload.currencies).containsKey(assetA.market.currency.code)

        val firstDto = payload.trns.first()
        assertThat(firstDto.assetId).isEqualTo(assetA.id)
        assertThat(firstDto.portfolioId).isEqualTo(portfolio.id)
        assertThat(firstDto.tradeCurrencyCode).isEqualTo(assetA.market.currency.code)
    }

    @Test
    fun `TrnResponse serialises with assetId portfolioId not embedded objects`() {
        val response = TrnResponse(listOf(buy("t1", assetA)))
        val json = BcJson.objectMapper.writeValueAsString(response)

        assertThat(json).contains("\"assetId\":\"${assetA.id}\"")
        assertThat(json).contains("\"portfolioId\":\"${portfolio.id}\"")
        assertThat(json).contains("\"assets\"")
        assertThat(json).contains("\"portfolios\"")
        // No embedded asset/portfolio object on the Trn record itself.
        val trnSegment = json.substringAfter("\"trns\":[").substringBefore("]")
        assertThat(trnSegment).doesNotContain("\"asset\":{")
        assertThat(trnSegment).doesNotContain("\"portfolio\":{")
    }

    @Test
    fun `TrnResponse round-trips through Jackson`() {
        val original =
            TrnResponse(
                listOf(
                    buy("t1", assetA),
                    buy("t2", assetB)
                )
            )
        val json = BcJson.objectMapper.writeValueAsString(original)
        val parsed = BcJson.objectMapper.readValue<TrnResponse>(json)

        assertThat(parsed.data.trns).hasSize(2)
        assertThat(parsed.data.assets).hasSize(2)
        assertThat(parsed.data.portfolios).hasSize(1)
        val firstAsset =
            parsed.data.assets[
                parsed.data.trns
                    .first()
                    .assetId
            ]
        assertThat(firstAsset?.code).isEqualTo(assetA.code)
    }
}