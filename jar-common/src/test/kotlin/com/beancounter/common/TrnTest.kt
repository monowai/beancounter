package com.beancounter.common

import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils
import com.beancounter.common.utils.PortfolioUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Trn related defaults
 */
class TrnTest {

    private val simpleRef = "simpleRef"

    @Test
    fun is_trnVersion() {
        val trnDefault = Trn(TrnType.BUY, AssetUtils.getAsset(Constants.NYSE, simpleRef))
        val trn = Trn(TrnType.BUY, version = "0", asset = AssetUtils.getAsset(Constants.NYSE, simpleRef))
        assertThat(trnDefault)
            .hasFieldOrProperty("version")
        assertThat(trn.version).isEqualTo("0")
        assertThat(trn.version).isNotEqualTo(trnDefault.version)
    }

    @Test
    fun is_TrnIdDefaulting() {
        val fromNull: CallerRef = CallerRef.from(null, null)
        assertThat(fromNull).hasNoNullFieldsOrProperties()
        val id = CallerRef("provider", "batch", "456")
        assertThat(CallerRef.from(id, null)).usingRecursiveComparison().isEqualTo(id)
    }

    @Test
    fun is_TrnIdDefaults() {
        var callerRef = CallerRef()
        assertThat(callerRef).hasAllNullFieldsOrProperties()
        val batchProp = "batch"
        val providerProp = "provider"
        val callerIdProp = "callerId"

        val code = "BLAH"
        assertThat(CallerRef.from(callerRef, PortfolioUtils.getPortfolio(code)))
            .hasNoNullFieldsOrProperties()
            .hasFieldOrPropertyWithValue(batchProp, code)
        callerRef = CallerRef(simpleRef, simpleRef, simpleRef)
        assertThat(CallerRef.from(callerRef, PortfolioUtils.getPortfolio(code)))
            .hasFieldOrPropertyWithValue(batchProp, simpleRef)
            .hasFieldOrPropertyWithValue(providerProp, simpleRef)
            .hasFieldOrPropertyWithValue(callerIdProp, simpleRef)

        // Called ID not specified
        callerRef = CallerRef(simpleRef, simpleRef)
        assertThat(CallerRef.from(callerRef, PortfolioUtils.getPortfolio(code)))
            .hasFieldOrPropertyWithValue(batchProp, simpleRef)
            .hasFieldOrPropertyWithValue(providerProp, simpleRef)
            .hasFieldOrProperty(callerIdProp)
    }

    @Test
    fun is_TradeCurrencySetFromAsset() {
        val trn = Trn(TrnType.BUY, AssetUtils.getAsset(Constants.NYSE, simpleRef))
        assertThat(trn.asset.market.currency).isNotNull
        assertThat(trn.tradeCurrency.code).isEqualTo(trn.asset.market.currency.code)
    }
}
