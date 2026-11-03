package com.beancounter.event

import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.model.Market
import com.beancounter.common.model.Position
import com.beancounter.common.model.QuantityValues
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.event.Constants.Companion.KMI
import com.beancounter.event.service.EventBehaviourFactory
import com.beancounter.event.service.alpha.AlphaEventConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal

/**
 * The factory must hand out the configured adapter, so the pay-date offset follows
 * `beancounter.events.dividend.days-to-add` rather than the adapter's built-in default.
 */
@SpringBootTest(
    classes = [AlphaEventConfig::class],
    properties = ["beancounter.events.dividend.days-to-add=$CONFIGURED_DAYS"]
)
class EventBehaviourFactoryTest {
    @Autowired
    private lateinit var behaviourFactory: EventBehaviourFactory

    @Test
    fun `should pay dividends after the configured number of days`() {
        val asset = getTestAsset(Market("NASDAQ"), KMI)
        val recordDate = DateUtils().date
        val position = Position(asset)
        position.quantityValues = QuantityValues(purchased = BigDecimal("100"))
        val event =
            CorporateEvent(
                id = "configured-days",
                trnType = TrnType.DIVI,
                recordDate = recordDate,
                assetId = asset.id,
                rate = BigDecimal("0.25")
            )

        val trnEvent = behaviourFactory.getAdapter().calculate(getPortfolio(), position, event)

        assertThat(trnEvent.trnInput.tradeDate).isEqualTo(recordDate.plusDays(CONFIGURED_DAYS))
    }
}

private const val CONFIGURED_DAYS = 3L