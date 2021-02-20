package com.beancounter.event

import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.beancounter.common.model.Position
import com.beancounter.common.model.QuantityValues
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.event.service.EventBehaviourFactory
import com.beancounter.event.service.alpha.AlphaEventAdapter
import com.beancounter.event.service.alpha.AlphaEventConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal

@SpringBootTest(classes = [AlphaEventConfig::class])
class TestAlphaEvents {
    @Autowired
    private lateinit var alphaEventAdapter: AlphaEventAdapter

    @Test
    fun is_UsDividendCalculated() {
        val market = Market("NASDAQ", USD)
        val asset = getAsset(market, "KMI")
        assertThat(asset.id).isNotNull()
        val quantityValues = QuantityValues()
        quantityValues.purchased = BigDecimal("80")
        val position = Position(asset)
        position.quantityValues = quantityValues
        assertThat(position.quantityValues.getTotal()).isEqualTo(BigDecimal("80"))
        val dateUtils = DateUtils()
        val onDate = dateUtils.getDate("2020-05-01")
        assertThat(onDate).isNotNull()
        val event = CorporateEvent(
            TrnType.DIVI,
            "ALPHA",
            asset.id!!,
            onDate!!,
            BigDecimal("0.2625")
        )
        val portfolio = getPortfolio("TEST", USD)
        val trnEvent = alphaEventAdapter.calculate(portfolio, position, event)
        assertThat(trnEvent).isNotNull
        assertThat(trnEvent?.portfolio).isNotNull
        assertThat(trnEvent?.trnInput)
            .isNotNull
            .hasFieldOrPropertyWithValue("assetId", asset.id)
            .hasFieldOrPropertyWithValue("trnType", TrnType.DIVI)
            .hasFieldOrPropertyWithValue("status", TrnStatus.PROPOSED)
            .hasFieldOrPropertyWithValue("tradeDate", dateUtils.getDate("2020-05-19"))
            .hasFieldOrPropertyWithValue("price", event.rate)
            .hasFieldOrPropertyWithValue("tax", BigDecimal("6.30")) // @ 30%
            .hasFieldOrPropertyWithValue("tradeAmount", BigDecimal("14.70"))
    }

    @Test
    fun is_FutureDatedTrnIgnored() {
        val market = Market("NASDAQ", USD)
        val asset = getAsset(market, "KMI")
        assertThat(asset.id).isNotNull()
        val dateUtils = DateUtils()
        val today = dateUtils.date
        assertThat(today).isNotNull()
        val event = CorporateEvent(
            TrnType.DIVI,
            "ALPHA",
            asset.id!!,
            today!!,
            BigDecimal("0.2625")
        )
        val behaviourFactory = EventBehaviourFactory()
        val portfolio = getPortfolio("TEST", USD)
        assertThat(
            behaviourFactory
                .getAdapter(event)
                ?.calculate(portfolio, Position(asset), event)
        )
            .isNull()
    }

    companion object {
        private val USD = Currency("USD")
    }
}
