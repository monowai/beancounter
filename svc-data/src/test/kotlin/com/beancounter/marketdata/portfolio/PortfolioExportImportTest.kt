package com.beancounter.marketdata.portfolio

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.Registration
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.marketdata.SpringMvcDbTest
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private const val PORTFOLIO_ROOT = "/portfolios"

/**
 * Tests for portfolio export and import functionality.
 */
@SpringMvcDbTest
class PortfolioExportImportTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var portfolioService: PortfolioService

    @Autowired
    private lateinit var systemUserService: Registration

    @Test
    fun `export creates valid CSV with headers`() {
        val sysUser = SystemUser(id = "export-portfolio-user")
        val token = mockAuthConfig.login(sysUser, systemUserService)

        // Create test portfolios
        portfolioService.save(
            listOf(
                PortfolioInput(
                    code = "EXPORT-TEST",
                    name = "Export Test Portfolio",
                    currency = "USD",
                    base = "USD"
                ),
                PortfolioInput(
                    code = "EXPORT-NZD",
                    name = "NZD Portfolio",
                    currency = "NZD",
                    base = "NZD"
                )
            )
        )

        // Export and verify CSV content
        val result =
            mockMvc
                .perform(
                    get("$PORTFOLIO_ROOT/export")
                        .with(jwt().jwt(token))
                ).andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.TEXT_PLAIN_VALUE))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"portfolios.csv\""))
                .andReturn()

        val csvContent = result.response.contentAsString
        val lines = csvContent.lines().filter { it.isNotBlank() }

        assertThat(lines).hasSizeGreaterThanOrEqualTo(3) // Header + 2 portfolios
        assertThat(lines[0]).isEqualTo("Code,Name,Currency,Base")
        assertThat(lines).anyMatch { it.contains("EXPORT-TEST") && it.contains("Export Test Portfolio") }
        assertThat(lines).anyMatch { it.contains("EXPORT-NZD") && it.contains("NZD Portfolio") }
    }

    @Test
    fun `import creates portfolios from CSV`() {
        val sysUser = SystemUser(id = "import-portfolio-user")
        val token = mockAuthConfig.login(sysUser, systemUserService)

        val csvContent =
            """
            Code,Name,Currency,Base
            IMPORT-USD,USD Import Portfolio,USD,USD
            IMPORT-EUR,EUR Portfolio,EUR,EUR
            """.trimIndent()

        val file =
            MockMultipartFile(
                "file",
                "portfolios.csv",
                MediaType.TEXT_PLAIN_VALUE,
                csvContent.toByteArray()
            )

        val result =
            mockMvc
                .perform(
                    multipart("$PORTFOLIO_ROOT/import")
                        .file(file)
                        .with(jwt().jwt(token))
                        .with(csrf())
                ).andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andReturn()

        val response = objectMapper.readValue<PortfoliosResponse>(result.response.contentAsString)
        assertThat(response.data).hasSize(2)
        assertThat(response.data.map { it.code }).containsExactlyInAnyOrder("IMPORT-USD", "IMPORT-EUR")
    }

    @Test
    fun `round trip export then import preserves data`() {
        val sysUser = SystemUser(id = "roundtrip-portfolio-user")
        val token = mockAuthConfig.login(sysUser, systemUserService)

        // Create original portfolio
        portfolioService.save(
            listOf(
                PortfolioInput(
                    code = "ROUNDTRIP",
                    name = "Round Trip Portfolio",
                    currency = "GBP",
                    base = "USD"
                )
            )
        )

        // Export
        val exportResult =
            mockMvc
                .perform(
                    get("$PORTFOLIO_ROOT/export")
                        .with(jwt().jwt(token))
                ).andExpect(status().isOk)
                .andReturn()

        val csvContent = exportResult.response.contentAsString

        // Import to a different user
        val newUser = SystemUser(id = "roundtrip-portfolio-user-2")
        val newToken = mockAuthConfig.login(newUser, systemUserService)

        val file =
            MockMultipartFile(
                "file",
                "portfolios.csv",
                MediaType.TEXT_PLAIN_VALUE,
                csvContent.toByteArray()
            )

        val importResult =
            mockMvc
                .perform(
                    multipart("$PORTFOLIO_ROOT/import")
                        .file(file)
                        .with(jwt().jwt(newToken))
                        .with(csrf())
                ).andExpect(status().isOk)
                .andReturn()

        val response = objectMapper.readValue<PortfoliosResponse>(importResult.response.contentAsString)
        val roundtripPortfolio = response.data.find { it.code == "ROUNDTRIP" }
        assertThat(roundtripPortfolio)
            .isNotNull
            .hasFieldOrPropertyWithValue("name", "Round Trip Portfolio")
        assertThat(roundtripPortfolio?.currency?.code).isEqualTo("GBP")
        assertThat(roundtripPortfolio?.base?.code).isEqualTo("USD")
    }
}