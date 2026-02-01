package com.beancounter.marketdata.assets

import com.beancounter.auth.AuthUtilService
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.registration.SystemUserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * Tests for AssetSearchService private asset search.
 * Verifies that PRIVATE market search matches on both code and name.
 */
@SpringMvcDbTest
class AssetSearchServiceTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var authUtilService: AuthUtilService

    @Autowired
    private lateinit var systemUserService: SystemUserService

    @Autowired
    private lateinit var assetSearchService: AssetSearchService

    @Autowired
    private lateinit var assetRepository: AssetRepository

    private lateinit var user: SystemUser

    @BeforeEach
    fun setUp() {
        val testUser = SystemUser(email = "search-test@test.com", auth0 = "auth0-search-test")
        authUtilService.authenticate(testUser, AuthUtilService.AuthProvider.AUTH0)
        systemUserService.register()
        user = systemUserService.getActiveUser()!!
    }

    @Test
    fun `search PRIVATE finds assets by code`() {
        createPrivateAsset("MY-PROP", "My Investment Property")

        val results = assetSearchService.search("MY-PROP", "PRIVATE")

        assertThat(results.data).isNotEmpty
        assertThat(results.data.first().symbol).isEqualTo("MY-PROP")
    }

    @Test
    fun `search PRIVATE finds assets by name`() {
        createPrivateAsset("MY-PROP", "My Investment Property")

        val results = assetSearchService.search("Investment", "PRIVATE")

        assertThat(results.data).isNotEmpty
        assertThat(results.data.first().name).isEqualTo("My Investment Property")
    }

    @Test
    fun `search PRIVATE does not return other users assets`() {
        createPrivateAsset("SECRET", "Other User Asset")

        // Switch to a different user
        val otherUser = SystemUser(email = "other@test.com", auth0 = "auth0-other")
        authUtilService.authenticate(otherUser, AuthUtilService.AuthProvider.AUTH0)
        systemUserService.register()

        val results = assetSearchService.search("SECRET", "PRIVATE")

        assertThat(results.data).isEmpty()
    }

    private fun createPrivateAsset(
        code: String,
        name: String
    ) {
        val privateMarket = Market("PRIVATE")
        assetRepository.save(
            Asset(
                id = "pvt-${code.lowercase()}",
                code = "${user.id}.$code",
                name = name,
                market = privateMarket,
                marketCode = "PRIVATE",
                category = "RE",
                systemUser = user
            )
        )
    }
}