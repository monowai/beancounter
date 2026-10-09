package com.beancounter.marketdata.classification

import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.markets.MarketConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * The classification opt-in lives at `beancounter.classification.providers`, outside the
 * `beancounter.market.providers` map that [MarketConfig] binds as `Map<String, Market>`.
 * Both must load in one context with the opt-in set: the previous key,
 * `beancounter.market.providers.classification`, was bound as a map entry and failed
 * bc-data at startup (#1176).
 */
@SpringBootTest(
    classes = [MarketConfig::class, ClassificationEnricherConfig::class],
    properties = ["beancounter.classification.providers=sec,alpha"]
)
class ClassificationProviderPropertyTest {
    @MockitoBean
    private lateinit var alpha: AlphaClassificationEnricher

    @MockitoBean
    private lateinit var eodhd: EodhdClassificationEnricher

    @MockitoBean
    private lateinit var sec: SecClassificationEnricher

    @MockitoBean
    private lateinit var currencyService: CurrencyService

    @Autowired
    private lateinit var classificationEnricher: ClassificationEnricher

    @Autowired
    private lateinit var marketConfig: MarketConfig

    @Test
    fun `should bind the classification opt-in beside the market providers map`() {
        assertThat(classificationEnricher).isInstanceOf(ChainedClassificationEnricher::class.java)
        assertThat(marketConfig.values).isNotEmpty()
    }
}