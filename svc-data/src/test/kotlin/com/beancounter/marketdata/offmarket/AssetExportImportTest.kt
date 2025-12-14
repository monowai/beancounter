package com.beancounter.marketdata.offmarket

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.Registration
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetIoDefinition
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.utils.ASSET_ROOT
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

/**
 * Tests for asset export and import functionality.
 */
@SpringMvcDbTest
class AssetExportImportTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var systemUserService: Registration

    @Autowired
    private lateinit var assetIoDefinition: AssetIoDefinition

    private val testUserId = "export-import-test-user"

    @Test
    fun `export creates valid CSV with headers`() {
        val sysUser = SystemUser(id = testUserId)
        val token = mockAuthConfig.login(sysUser, systemUserService)

        // Create test assets
        val savingsAccount =
            AssetInput.toAccount(
                currency = USD,
                code = "EXPORT-SAVINGS",
                name = "Export Test Savings",
                owner = testUserId
            )
        val checkingAccount =
            AssetInput.toAccount(
                currency = NZD,
                code = "EXPORT-CHECKING",
                name = "Export Test Checking",
                owner = testUserId
            )
        assetService.handle(
            AssetRequest(
                mapOf(
                    "savings" to savingsAccount,
                    "checking" to checkingAccount
                )
            )
        )

        // Export and verify CSV content
        val result =
            mockMvc
                .perform(
                    get("$ASSET_ROOT/me/export")
                        .with(jwt().jwt(token))
                ).andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.TEXT_PLAIN_VALUE))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"assets.csv\""))
                .andReturn()

        val csvContent = result.response.contentAsString
        val lines = csvContent.lines().filter { it.isNotBlank() }

        assertThat(lines).hasSizeGreaterThanOrEqualTo(3) // Header + 2 assets
        assertThat(lines[0]).isEqualTo("Code,Name,Category,Currency")
        assertThat(lines).anyMatch { it.contains("EXPORT-SAVINGS") && it.contains("Export Test Savings") }
        assertThat(lines).anyMatch { it.contains("EXPORT-CHECKING") && it.contains("Export Test Checking") }
    }

    @Test
    fun `import creates assets from CSV`() {
        val sysUser = SystemUser(id = "import-test-user")
        val token = mockAuthConfig.login(sysUser, systemUserService)

        val csvContent =
            """
            Code,Name,Category,Currency
            IMPORT-WISE,Wise Account,ACCOUNT,USD
            IMPORT-PROP,My Property,RE,NZD
            """.trimIndent()

        val file =
            MockMultipartFile(
                "file",
                "assets.csv",
                MediaType.TEXT_PLAIN_VALUE,
                csvContent.toByteArray()
            )

        val result =
            mockMvc
                .perform(
                    multipart("$ASSET_ROOT/me/import")
                        .file(file)
                        .with(jwt().jwt(token))
                        .with(csrf())
                ).andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andReturn()

        val response = objectMapper.readValue<AssetUpdateResponse>(result.response.contentAsString)
        assertThat(response.data).hasSize(2)

        val wiseAsset = response.data["IMPORT-WISE"]
        assertThat(wiseAsset)
            .isNotNull
            .hasFieldOrPropertyWithValue("name", "Wise Account")
            .hasFieldOrPropertyWithValue("priceSymbol", "USD")
        assertThat(wiseAsset?.assetCategory?.id).isEqualTo("ACCOUNT")

        val propAsset = response.data["IMPORT-PROP"]
        assertThat(propAsset)
            .isNotNull
            .hasFieldOrPropertyWithValue("name", "My Property")
            .hasFieldOrPropertyWithValue("priceSymbol", "NZD")
        assertThat(propAsset?.assetCategory?.id).isEqualTo("RE")
    }

    @Test
    fun `import without headers creates assets`() {
        val sysUser = SystemUser(id = "import-no-header-user")
        val token = mockAuthConfig.login(sysUser, systemUserService)

        val csvContent =
            """
            NO-HDR-SAVINGS,Savings No Header,ACCOUNT,USD
            """.trimIndent()

        val file =
            MockMultipartFile(
                "file",
                "assets.csv",
                MediaType.TEXT_PLAIN_VALUE,
                csvContent.toByteArray()
            )

        val result =
            mockMvc
                .perform(
                    multipart("$ASSET_ROOT/me/import")
                        .file(file)
                        .with(jwt().jwt(token))
                        .with(csrf())
                ).andExpect(status().isOk)
                .andReturn()

        val response = objectMapper.readValue<AssetUpdateResponse>(result.response.contentAsString)
        assertThat(response.data).hasSize(1)
        assertThat(response.data["NO-HDR-SAVINGS"])
            .isNotNull
            .hasFieldOrPropertyWithValue("name", "Savings No Header")
    }

    @Test
    fun `round trip export then import preserves data`() {
        val sysUser = SystemUser(id = "round-trip-user")
        val token = mockAuthConfig.login(sysUser, systemUserService)

        // Create original assets
        val originalAsset =
            AssetInput.toAccount(
                currency = USD,
                code = "ROUNDTRIP",
                name = "Round Trip Account",
                owner = sysUser.id
            )
        assetService.handle(AssetRequest(mapOf("roundtrip" to originalAsset)))

        // Export
        val exportResult =
            mockMvc
                .perform(
                    get("$ASSET_ROOT/me/export")
                        .with(jwt().jwt(token))
                ).andExpect(status().isOk)
                .andReturn()

        val csvContent = exportResult.response.contentAsString

        // Clear the user's assets by using a different user
        val newUser = SystemUser(id = "round-trip-user-2")
        val newToken = mockAuthConfig.login(newUser, systemUserService)

        // Import to new user
        val file =
            MockMultipartFile(
                "file",
                "assets.csv",
                MediaType.TEXT_PLAIN_VALUE,
                csvContent.toByteArray()
            )

        val importResult =
            mockMvc
                .perform(
                    multipart("$ASSET_ROOT/me/import")
                        .file(file)
                        .with(jwt().jwt(newToken))
                        .with(csrf())
                ).andExpect(status().isOk)
                .andReturn()

        val response = objectMapper.readValue<AssetUpdateResponse>(importResult.response.contentAsString)
        assertThat(response.data["ROUNDTRIP"])
            .isNotNull
            .hasFieldOrPropertyWithValue("name", "Round Trip Account")
    }
}