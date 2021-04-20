package com.beancounter.client.integ

import com.beancounter.client.Constants.Companion.portfolio
import com.beancounter.client.config.ClientConfig
import com.beancounter.client.services.TrnService
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.utils.DateUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
/**
 * Basic client side transaction tests.
 */
@AutoConfigureStubRunner(stubsMode = StubRunnerProperties.StubsMode.LOCAL, ids = ["org.beancounter:svc-data:+:stubs:10999"])
@ImportAutoConfiguration(ClientConfig::class)
@SpringBootTest(classes = [ClientConfig::class])
class TestTrnService {
    @Autowired
    private lateinit var trnService: TrnService

    @Test
    fun is_TrnsReturnedForPortfolioId() {
        val trnResponse = trnService.query(portfolio)
        assertThat(trnResponse).isNotNull.hasFieldOrProperty("data")
        assertThat(trnResponse.data).isNotEmpty // Don't care about the contents here.
    }

    @Test
    fun is_TrnsReturnedForPortfolioAssetId() {
        val query = TrustedTrnQuery(
            portfolio, DateUtils().getDate("2020-05-01"), "KMI"
        )
        val queryResults = trnService.query(query)
        assertThat(queryResults).isNotNull.hasFieldOrProperty("data")
        assertThat(queryResults.data).isNotEmpty // Don't care about the contents here.
    }
}
