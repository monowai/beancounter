package com.beancounter.marketdata.registration

import com.beancounter.auth.AuthUtilService
import com.beancounter.common.contracts.UserPreferencesRequest
import com.beancounter.common.model.GroupByPreference
import com.beancounter.common.model.HoldingsView
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.ValueInPreference
import com.beancounter.marketdata.SpringMvcDbTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * Verify user preferences service functionality.
 */
@SpringMvcDbTest
class UserPreferencesServiceTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    lateinit var userPreferencesService: UserPreferencesService

    @Autowired
    lateinit var userPreferencesRepository: UserPreferencesRepository

    @Autowired
    lateinit var systemUserService: SystemUserService

    @Autowired
    lateinit var authUtilService: AuthUtilService

    @Test
    fun `should create default preferences for new user with no existing preferences`() {
        val user = createAndAuthenticateUser("prefs-test-1@email.com")

        // Verify no preferences exist for this user
        assertThat(userPreferencesRepository.findByOwner(user)).isEmpty

        // Call getOrCreate - should create defaults
        val preferences = userPreferencesService.getOrCreate(user)

        // Verify defaults are set correctly
        assertThat(preferences)
            .isNotNull
            .hasFieldOrPropertyWithValue("preferredName", null)
            .hasFieldOrPropertyWithValue("defaultHoldingsView", HoldingsView.SUMMARY)
            .hasFieldOrPropertyWithValue("defaultValueIn", ValueInPreference.PORTFOLIO)
            .hasFieldOrPropertyWithValue("defaultGroupBy", GroupByPreference.ASSET_CLASS)
            .hasFieldOrPropertyWithValue("baseCurrencyCode", "USD")
        assertThat(preferences.owner.id).isEqualTo(user.id)

        // Verify preferences are now persisted
        assertThat(userPreferencesRepository.findByOwner(user)).isPresent
    }

    @Test
    fun `should return existing preferences on second call`() {
        val user = createAndAuthenticateUser("prefs-test-2@email.com")

        val preferences1 = userPreferencesService.getOrCreate(user)
        val preferences2 = userPreferencesService.getOrCreate(user)

        assertThat(preferences1.id).isEqualTo(preferences2.id)
    }

    @Test
    fun `should update holdings view preference`() {
        val user = createAndAuthenticateUser("prefs-test-3@email.com")

        val request = UserPreferencesRequest(defaultHoldingsView = HoldingsView.TABLE)
        val updated = userPreferencesService.update(user, request)

        assertThat(updated.defaultHoldingsView).isEqualTo(HoldingsView.TABLE)
        assertThat(updated.baseCurrencyCode).isEqualTo("USD") // unchanged
    }

    @Test
    fun `should update base currency preference`() {
        val user = createAndAuthenticateUser("prefs-test-4@email.com")

        val request = UserPreferencesRequest(baseCurrencyCode = "SGD")
        val updated = userPreferencesService.update(user, request)

        assertThat(updated.baseCurrencyCode).isEqualTo("SGD")
        assertThat(updated.defaultHoldingsView).isEqualTo(HoldingsView.SUMMARY) // unchanged
    }

    @Test
    fun `should update both preferences at once`() {
        val user = createAndAuthenticateUser("prefs-test-5@email.com")

        val request =
            UserPreferencesRequest(
                defaultHoldingsView = HoldingsView.HEATMAP,
                baseCurrencyCode = "EUR"
            )
        val updated = userPreferencesService.update(user, request)

        assertThat(updated.defaultHoldingsView).isEqualTo(HoldingsView.HEATMAP)
        assertThat(updated.baseCurrencyCode).isEqualTo("EUR")
    }

    @Test
    fun `should update preferred name`() {
        val user = createAndAuthenticateUser("prefs-test-7@email.com")

        val request = UserPreferencesRequest(preferredName = "Mike")
        val updated = userPreferencesService.update(user, request)

        assertThat(updated.preferredName).isEqualTo("Mike")
        assertThat(updated.defaultHoldingsView).isEqualTo(HoldingsView.SUMMARY) // unchanged
        assertThat(updated.baseCurrencyCode).isEqualTo("USD") // unchanged
    }

    @Test
    fun `should handle partial updates with null values`() {
        val user = createAndAuthenticateUser("prefs-test-6@email.com")

        // First set some values
        userPreferencesService.update(
            user,
            UserPreferencesRequest(
                defaultHoldingsView = HoldingsView.ALLOCATION,
                baseCurrencyCode = "GBP"
            )
        )

        // Now update only one field
        val request = UserPreferencesRequest(baseCurrencyCode = "JPY")
        val updated = userPreferencesService.update(user, request)

        assertThat(updated.defaultHoldingsView).isEqualTo(HoldingsView.ALLOCATION) // unchanged
        assertThat(updated.baseCurrencyCode).isEqualTo("JPY")
    }

    @Test
    fun `should update defaultValueIn preference`() {
        val user = createAndAuthenticateUser("prefs-test-8@email.com")

        val request = UserPreferencesRequest(defaultValueIn = ValueInPreference.BASE)
        val updated = userPreferencesService.update(user, request)

        assertThat(updated.defaultValueIn).isEqualTo(ValueInPreference.BASE)
        assertThat(updated.defaultGroupBy).isEqualTo(GroupByPreference.ASSET_CLASS) // unchanged
    }

    @Test
    fun `should update defaultGroupBy preference`() {
        val user = createAndAuthenticateUser("prefs-test-9@email.com")

        val request = UserPreferencesRequest(defaultGroupBy = GroupByPreference.SECTOR)
        val updated = userPreferencesService.update(user, request)

        assertThat(updated.defaultGroupBy).isEqualTo(GroupByPreference.SECTOR)
        assertThat(updated.defaultValueIn).isEqualTo(ValueInPreference.PORTFOLIO) // unchanged
    }

    @Test
    fun `should update all preferences at once`() {
        val user = createAndAuthenticateUser("prefs-test-10@email.com")

        val request =
            UserPreferencesRequest(
                preferredName = "Test User",
                defaultHoldingsView = HoldingsView.TABLE,
                defaultValueIn = ValueInPreference.TRADE,
                defaultGroupBy = GroupByPreference.MARKET_CURRENCY,
                baseCurrencyCode = "NZD"
            )
        val updated = userPreferencesService.update(user, request)

        assertThat(updated.preferredName).isEqualTo("Test User")
        assertThat(updated.defaultHoldingsView).isEqualTo(HoldingsView.TABLE)
        assertThat(updated.defaultValueIn).isEqualTo(ValueInPreference.TRADE)
        assertThat(updated.defaultGroupBy).isEqualTo(GroupByPreference.MARKET_CURRENCY)
        assertThat(updated.baseCurrencyCode).isEqualTo("NZD")
    }

    @Test
    fun `should preserve existing preferences when updating only new fields`() {
        val user = createAndAuthenticateUser("prefs-test-11@email.com")

        // First set legacy preferences
        userPreferencesService.update(
            user,
            UserPreferencesRequest(
                defaultHoldingsView = HoldingsView.HEATMAP,
                baseCurrencyCode = "AUD"
            )
        )

        // Now update only new fields
        val request =
            UserPreferencesRequest(
                defaultValueIn = ValueInPreference.BASE,
                defaultGroupBy = GroupByPreference.MARKET
            )
        val updated = userPreferencesService.update(user, request)

        // Legacy preferences unchanged
        assertThat(updated.defaultHoldingsView).isEqualTo(HoldingsView.HEATMAP)
        assertThat(updated.baseCurrencyCode).isEqualTo("AUD")
        // New preferences updated
        assertThat(updated.defaultValueIn).isEqualTo(ValueInPreference.BASE)
        assertThat(updated.defaultGroupBy).isEqualTo(GroupByPreference.MARKET)
    }

    private fun createAndAuthenticateUser(email: String): SystemUser {
        val user = SystemUser(email = email, auth0 = "auth0-$email")
        authUtilService.authenticate(user, AuthUtilService.AuthProvider.AUTH0)
        systemUserService.register()
        return systemUserService.getActiveUser()!!
    }
}