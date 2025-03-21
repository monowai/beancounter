package com.beancounter.common.trn

import com.beancounter.common.Constants
import com.beancounter.common.V_BATCH
import com.beancounter.common.V_PROVIDER
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PortfolioUtils
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Trn related defaults
 */
class TrnTest {
    private val simpleRef = "simpleRef"
    private val batch =
        DateUtils().getFormattedDate().toString().replace(
            "-",
            ""
        )

    @Test
    fun is_trnVersion() {
        val id = "any"
        val trnDefault =
            Trn(
                id = id,
                trnType = TrnType.BUY,
                asset =
                    AssetUtils.getTestAsset(
                        Constants.NYSE,
                        simpleRef
                    ),
                portfolio = PortfolioUtils.getPortfolio()
            )
        val trn =
            Trn(
                id = id,
                trnType = TrnType.BUY,
                asset =
                    AssetUtils.getTestAsset(
                        Constants.NYSE,
                        simpleRef
                    ),
                portfolio = PortfolioUtils.getPortfolio(),
                version = "0"
            )
        Assertions
            .assertThat(trnDefault)
            .hasFieldOrProperty("version")
        Assertions.assertThat(trn.version).isEqualTo("0")
        Assertions.assertThat(trn.version).isNotEqualTo(trnDefault.version)
    }

    @Test
    fun is_TrnIdDefaulting() {
        val fromNull: CallerRef = CallerRef.from(callerRef = CallerRef())
        Assertions.assertThat(fromNull).hasNoNullFieldsOrProperties()
        val id =
            CallerRef(
                V_PROVIDER,
                V_BATCH,
                "456"
            )
        Assertions.assertThat(CallerRef.from(id)).usingRecursiveComparison().isEqualTo(id)
    }

    @Test
    fun callerRefDefaults() {
        val fromNull: CallerRef = CallerRef.from(CallerRef())
        Assertions
            .assertThat(fromNull)
            .hasNoNullFieldsOrProperties()
            .hasFieldOrPropertyWithValue(
                V_PROVIDER,
                "BC"
            ).hasFieldOrPropertyWithValue(
                V_BATCH,
                batch
            ) // Defaults to today
    }

    @Test
    fun is_TrnIdDefaults() {
        var callerRef = CallerRef()
        Assertions.assertThat(callerRef).hasNoNullFieldsOrProperties()
        val batchProp = V_BATCH
        val providerProp = V_PROVIDER
        val callerIdProp = "callerId"

        Assertions
            .assertThat(CallerRef.from(callerRef))
            .hasNoNullFieldsOrProperties()
            .hasFieldOrPropertyWithValue(
                batchProp,
                batch
            )
        callerRef =
            CallerRef(
                simpleRef,
                simpleRef,
                simpleRef
            )
        Assertions
            .assertThat(CallerRef.from(callerRef))
            .hasFieldOrPropertyWithValue(
                batchProp,
                simpleRef
            ).hasFieldOrPropertyWithValue(
                providerProp,
                simpleRef
            ).hasFieldOrPropertyWithValue(
                callerIdProp,
                simpleRef
            )

        // Called ID not specified
        callerRef =
            CallerRef(
                simpleRef,
                simpleRef
            )
        Assertions
            .assertThat(CallerRef.from(callerRef))
            .hasFieldOrPropertyWithValue(
                batchProp,
                simpleRef
            ).hasFieldOrPropertyWithValue(
                providerProp,
                simpleRef
            ).hasFieldOrProperty(callerIdProp)
    }

    @Test
    fun is_TradeCurrencySetFromAsset() {
        val trn =
            Trn(
                id = "any",
                trnType = TrnType.BUY,
                asset =
                    AssetUtils.getTestAsset(
                        Constants.NYSE,
                        simpleRef
                    ),
                portfolio = PortfolioUtils.getPortfolio()
            )
        Assertions.assertThat(trn.asset.market.currency).isNotNull
        Assertions.assertThat(trn.tradeCurrency.code).isEqualTo(trn.asset.market.currency.code)
    }

    @Test
    fun fm_Json() {
        val json =
            "{\"tradePortfolioRate\":1,\"tradeCashRate\":1,\"tradeBaseRate\":0.69662,\"price\":429.265,\"tax\":0," +
                "\"fees\":1.08,\"cashAmount\":-859.6,\"tradeAmount\":859.6,\"quantity\":2,\"tradeCurrency\":\"USD\"," +
                "\"trnType\":\"BUY\",\"tradeDate\":\"2021-10-06\"}"
        Assertions.assertThat(BcJson.objectMapper.readValue<TrnInput>(json)).isNotNull
    }
}