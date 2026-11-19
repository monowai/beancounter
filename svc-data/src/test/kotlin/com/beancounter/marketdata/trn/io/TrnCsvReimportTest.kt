package com.beancounter.marketdata.trn.io

import com.beancounter.auth.MockAuthConfig
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.input.ImportFormat
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.TrnStatus
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.Constants.Companion.msftInput
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.enrichment.DefaultEnricher
import com.beancounter.marketdata.assets.enrichment.EnrichmentFactory
import com.beancounter.marketdata.assets.figi.FigiProxy
import com.beancounter.marketdata.trn.TrnRepository
import com.beancounter.marketdata.utils.BcMvcHelper
import com.beancounter.marketdata.utils.RegistrationUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Re-importing a CSV row whose caller reference (batch and caller id) is
 * already recorded in the portfolio updates that transaction instead of
 * adding another, so a corrected file can be imported again.
 */
@SpringMvcDbTest
class TrnCsvReimportTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var figiProxy: FigiProxy

    @MockitoBean
    private lateinit var fxTransactions: FxTransactions

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var trnImportService: TrnImportService

    @Autowired
    private lateinit var trnRepository: TrnRepository

    @Autowired
    private lateinit var enrichmentFactory: EnrichmentFactory

    @Autowired
    private lateinit var defaultEnricher: DefaultEnricher

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    private lateinit var bcMvcHelper: BcMvcHelper

    @BeforeEach
    fun configure() {
        enrichmentFactory.register(defaultEnricher)
        val token = mockAuthConfig.getUserToken(Constants.systemUser)
        bcMvcHelper = BcMvcHelper(mockMvc, token)
        RegistrationUtils.registerUser(mockMvc, token)
        bcMvcHelper.asset(AssetRequest(msftInput))
    }

    private fun recorded(portfolioId: String) =
        trnRepository.findByPortfolioId(portfolioId, LocalDate.of(2100, 1, 1), TrnStatus.SETTLED).toList()

    private fun row(
        batch: String,
        callerId: String,
        quantity: String,
        price: String
    ): List<String> =
        listOf(
            batch,
            callerId,
            "BUY",
            "US",
            "MSFT",
            "",
            "",
            "USD",
            "2024-02-12",
            quantity,
            "",
            "USD",
            price,
            "0",
            "",
            "",
            "",
            ""
        )

    @Test
    fun `should update the recorded transaction when a row with the same caller reference is imported again`() {
        val portfolio =
            bcMvcHelper.portfolio(PortfolioInput("REIMPORT", "Re-import", currency = USD.code))
        val first =
            trnImportService.fromCsvImport(
                TrustedTrnImportRequest(portfolio, ImportFormat.BC, row = row("20240212", "T-1", "10", "400"))
            )

        val second =
            trnImportService.fromCsvImport(
                TrustedTrnImportRequest(portfolio, ImportFormat.BC, row = row("20240212", "T-1", "12", "410"))
            )

        val recorded = recorded(portfolio.id)
        assertThat(recorded).hasSize(1)
        assertThat(recorded.single().id).isEqualTo(first.single().id).isEqualTo(second.single().id)
        assertThat(recorded.single().quantity).isEqualByComparingTo(BigDecimal("12"))
        assertThat(recorded.single().price).isEqualByComparingTo(BigDecimal("410"))
    }

    @Test
    fun `should add a transaction when the caller id is new`() {
        val portfolio =
            bcMvcHelper.portfolio(PortfolioInput("REIMPORT2", "Re-import new", currency = USD.code))
        trnImportService.fromCsvImport(
            TrustedTrnImportRequest(portfolio, ImportFormat.BC, row = row("20240213", "T-1", "10", "400"))
        )

        trnImportService.fromCsvImport(
            TrustedTrnImportRequest(portfolio, ImportFormat.BC, row = row("20240213", "T-2", "5", "405"))
        )

        assertThat(recorded(portfolio.id)).hasSize(2)
    }
}