package com.beancounter.common

import com.beancounter.common.contracts.PriceRequest.Companion.dateUtils
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Trn related defaults
 */
class TrnTest {

    private val simpleRef = "simpleRef"
    private val batch = dateUtils.getDate().toString().replace("-", "")

    @Test
    fun is_trnVersion() {
        val trnDefault = Trn(
            id = "any",
            trnType = TrnType.BUY,
            asset = AssetUtils.getAsset(Constants.NYSE, simpleRef),
        )
        val trn = Trn(
            id = "any",
            trnType = TrnType.BUY,
            version = "0",
            asset = AssetUtils.getAsset(Constants.NYSE, simpleRef),
        )
        assertThat(trnDefault)
            .hasFieldOrProperty("version")
        assertThat(trn.version).isEqualTo("0")
        assertThat(trn.version).isNotEqualTo(trnDefault.version)
    }

    @Test
    fun is_TrnIdDefaulting() {
        val fromNull: CallerRef = CallerRef.from(callerRef = CallerRef())
        assertThat(fromNull).hasNoNullFieldsOrProperties()
        val id = CallerRef("provider", "batch", "456")
        assertThat(CallerRef.from(id)).usingRecursiveComparison().isEqualTo(id)
    }

    @Test
    fun callerRefDefaults() {
        val fromNull: CallerRef = CallerRef.from(CallerRef())
        assertThat(fromNull)
            .hasNoNullFieldsOrProperties()
            .hasFieldOrPropertyWithValue("provider", "BC")
            .hasFieldOrPropertyWithValue("batch", batch) // Defaults to today
    }

    @Test
    fun is_TrnIdDefaults() {
        var callerRef = CallerRef()
        assertThat(callerRef).hasNoNullFieldsOrProperties()
        val batchProp = "batch"
        val providerProp = "provider"
        val callerIdProp = "callerId"

        assertThat(CallerRef.from(callerRef))
            .hasNoNullFieldsOrProperties()
            .hasFieldOrPropertyWithValue(batchProp, batch)
        callerRef = CallerRef(simpleRef, simpleRef, simpleRef)
        assertThat(CallerRef.from(callerRef))
            .hasFieldOrPropertyWithValue(batchProp, simpleRef)
            .hasFieldOrPropertyWithValue(providerProp, simpleRef)
            .hasFieldOrPropertyWithValue(callerIdProp, simpleRef)

        // Called ID not specified
        callerRef = CallerRef(simpleRef, simpleRef)
        assertThat(CallerRef.from(callerRef))
            .hasFieldOrPropertyWithValue(batchProp, simpleRef)
            .hasFieldOrPropertyWithValue(providerProp, simpleRef)
            .hasFieldOrProperty(callerIdProp)
    }

    @Test
    fun is_TradeCurrencySetFromAsset() {
        val trn = Trn(
            id = "any",
            trnType = TrnType.BUY,
            asset = AssetUtils.getAsset(Constants.NYSE, simpleRef),
        )
        assertThat(trn.asset.market.currency).isNotNull
        assertThat(trn.tradeCurrency.code).isEqualTo(trn.asset.market.currency.code)
    }
}
