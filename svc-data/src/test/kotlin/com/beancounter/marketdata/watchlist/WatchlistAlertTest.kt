package com.beancounter.marketdata.watchlist

import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.TargetDirection
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetRepository
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.providers.MarketDataRepo
import com.beancounter.marketdata.providers.alpha.AlphaPriceService
import com.beancounter.marketdata.utils.RegistrationUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultMatcher
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.JsonNode
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Watchlist target alerts against real H2 state: the direction fixed when a target is set,
 * the post-refresh check that fires once per target, and the owner-scoped alert endpoints.
 * Users persist across tests and the check sweeps every armed item, so each test uses its
 * own assets and reads its alerts by item id.
 */
@SpringMvcDbTest
class WatchlistAlertTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    // No test calls out: the provider has no quotes, so views show the stored close.
    @MockitoBean
    private lateinit var alphaPriceService: AlphaPriceService

    @MockitoSpyBean
    private lateinit var alertRepository: WatchlistAlertRepository

    @Autowired
    private lateinit var cacheManager: CacheManager

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var assetRepository: AssetRepository

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var marketDataRepo: MarketDataRepo

    @Autowired
    private lateinit var watchlistItemRepository: WatchlistItemRepository

    @Autowired
    private lateinit var alertService: WatchlistAlertService

    @Autowired
    private lateinit var dateUtils: DateUtils

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private lateinit var tokenA: Jwt
    private lateinit var tokenB: Jwt

    @BeforeEach
    fun setUp() {
        tokenA = register("wla-user-a")
        tokenB = register("wla-user-b")
        cacheManager.getCache("provider")?.clear()
        whenever(alphaPriceService.getId()).thenReturn(AlphaPriceService.ID)
        whenever(alphaPriceService.isMarketSupported(any())).thenReturn(true)
        whenever(alphaPriceService.isApiSupported()).thenReturn(true)
        whenever(alphaPriceService.getDate(any(), any())).thenReturn(dateUtils.date)
    }

    private fun register(user: String): Jwt =
        RegistrationUtils.registerUser(mockMvc, mockAuthConfig.getUserToken(SystemUser(user, user)))

    private fun asset(code: String): Asset =
        assetRepository.save(
            Asset(code = code, id = KeyGenUtils().id, market = NASDAQ, marketCode = NASDAQ.code)
        )

    private fun close(
        asset: Asset,
        daysAgo: Long,
        close: String,
        split: String = "1"
    ): MarketData =
        marketDataRepo.save(
            MarketData(
                asset = asset,
                priceDate = dateUtils.date.minusDays(daysAgo),
                close = BigDecimal(close),
                split = BigDecimal(split)
            )
        )

    private fun call(
        request: MockHttpServletRequestBuilder,
        token: Jwt,
        expect: ResultMatcher = status().isOk,
        body: Any? = null
    ): JsonNode {
        val builder = request.with(jwt().jwt(token)).with(csrf())
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body))
        }
        val content =
            mockMvc
                .perform(builder)
                .andExpect(expect)
                .andReturn()
                .response.contentAsString
        return objectMapper.readTree(content.ifBlank { "{}" })
    }

    private fun watch(
        asset: Asset,
        addedPrice: Int,
        targetPrice: Int? = null,
        token: Jwt = tokenA
    ): String =
        call(
            post(ITEMS),
            token,
            body = mapOf("assetId" to asset.id, "addedPrice" to addedPrice, "targetPrice" to targetPrice)
        ).path("data").path("id").asString()

    private fun retarget(
        itemId: String,
        body: Map<String, Any>
    ): JsonNode = call(patch(ITEM, itemId), tokenA, body = body).path("data")

    private fun stored(itemId: String) = watchlistItemRepository.findById(itemId).orElseThrow()

    private fun alerts(token: Jwt = tokenA): List<JsonNode> = call(get(ALERTS), token).path("data").toList()

    private fun alertsFor(
        itemId: String,
        token: Jwt = tokenA
    ): List<JsonNode> = alerts(token).filter { it.path("itemId").asString() == itemId }

    private fun item(itemId: String): JsonNode =
        call(get(ROOT), tokenA)
            .path("data")
            .path("items")
            .first { it.path("id").asString() == itemId }

    /** Fixture: the target was entered [daysAgo] days ago (its share basis is that day's). */
    private fun backdateTarget(
        itemId: String,
        daysAgo: Long
    ) {
        val item = stored(itemId)
        watchlistItemRepository.save(
            item.copy(
                addedOn = dateUtils.date.minusDays(daysAgo),
                targetOn = dateUtils.date.minusDays(daysAgo)
            )
        )
    }

    @Test
    fun `should fix the direction from the current close when a target is set`() {
        val stock = asset("WLADIR")
        close(stock, 1, "100")
        val itemId = watch(stock, addedPrice = 90, targetPrice = 120)
        assertThat(stored(itemId).targetDirection).isEqualTo(TargetDirection.ABOVE)

        retarget(itemId, mapOf("targetPrice" to 80))
        assertThat(stored(itemId).targetDirection).isEqualTo(TargetDirection.BELOW)

        retarget(itemId, mapOf("clearTarget" to true))
        assertThat(stored(itemId).targetDirection).isNull()
        assertThat(stored(itemId).targetHitOn).isNull()
    }

    @Test
    fun `should take the direction from the added price when the asset has no close`() {
        val itemId = watch(asset("WLADIRADDED"), addedPrice = 10, targetPrice = 8)

        assertThat(stored(itemId).targetDirection).isEqualTo(TargetDirection.BELOW)
    }

    @Test
    fun `should raise an alert when the close crosses an above target`() {
        val stock = asset("WLAUP")
        close(stock, 1, "100")
        val itemId = watch(stock, addedPrice = 100, targetPrice = 120)
        close(stock, 0, "125")

        alertService.checkTargets()

        val alert = alertsFor(itemId).single()
        assertThat(alert.path("id").asString()).isNotBlank()
        assertThat(alert.path("asset").path("id").asString()).isEqualTo(stock.id)
        assertThat(alert.path("currency").path("code").asString()).isEqualTo("USD")
        assertThat(alert.path("direction").asString()).isEqualTo("ABOVE")
        assertThat(alert.path("targetPrice").decimalValue()).isEqualByComparingTo("120")
        assertThat(alert.path("close").decimalValue()).isEqualByComparingTo("125")
        assertThat(alert.path("priceDate").asString()).isEqualTo(dateUtils.date.toString())
        assertThat(Instant.parse(alert.path("createdAt").asString())).isBeforeOrEqualTo(Instant.now())
        assertThat(stored(itemId).targetHitOn).isEqualTo(dateUtils.date)
        assertThat(item(itemId).path("targetHitOn").asString()).isEqualTo(dateUtils.date.toString())
    }

    @Test
    fun `should raise an alert when the close falls to a below target`() {
        val stock = asset("WLADOWN")
        close(stock, 1, "100")
        val itemId = watch(stock, addedPrice = 100, targetPrice = 80)
        close(stock, 0, "80")

        alertService.checkTargets()

        val alert = alertsFor(itemId).single()
        assertThat(alert.path("direction").asString()).isEqualTo("BELOW")
        assertThat(alert.path("close").decimalValue()).isEqualByComparingTo("80")
    }

    @Test
    fun `should not alert while the close has not reached the target`() {
        val stock = asset("WLANOTYET")
        close(stock, 1, "100")
        val itemId = watch(stock, addedPrice = 100, targetPrice = 120)
        close(stock, 0, "119.99")

        alertService.checkTargets()

        assertThat(alertsFor(itemId)).isEmpty()
        assertThat(stored(itemId).targetHitOn).isNull()
        assertThat(item(itemId).path("targetHitOn").isNull).isTrue()
    }

    @Test
    fun `should ignore closes from before the target was set`() {
        val stock = asset("WLAOLD")
        val yesterday = close(stock, 1, "100")
        val itemId = watch(stock, addedPrice = 100, targetPrice = 120)
        // A late correction lifts the latest close past the target, but it predates the
        // target (set today), so it is not a crossing.
        marketDataRepo.save(yesterday.apply { close = BigDecimal("130") })

        alertService.checkTargets()

        assertThat(alertsFor(itemId)).isEmpty()
    }

    @Test
    fun `should alert only once per target`() {
        val stock = asset("WLAONCE")
        close(stock, 1, "100")
        val itemId = watch(stock, addedPrice = 100, targetPrice = 120)
        close(stock, 0, "125")

        alertService.checkTargets()
        alertService.checkTargets()

        assertThat(alertsFor(itemId)).hasSize(1)
    }

    @Test
    fun `should re-arm when the target is edited`() {
        val stock = asset("WLAREARM")
        close(stock, 1, "100")
        val itemId = watch(stock, addedPrice = 100, targetPrice = 120)
        val today = close(stock, 0, "125")
        alertService.checkTargets()
        assertThat(stored(itemId).targetHitOn).isNotNull()

        val edited = retarget(itemId, mapOf("targetPrice" to 140))
        assertThat(edited.path("targetHitOn").isNull).isTrue()
        assertThat(stored(itemId).targetDirection).isEqualTo(TargetDirection.ABOVE)
        marketDataRepo.save(today.apply { close = BigDecimal("141") })
        alertService.checkTargets()

        val fired = alertsFor(itemId)
        assertThat(fired).hasSize(2)
        assertThat(fired.first().path("targetPrice").decimalValue())
            .describedAs("newest first")
            .isEqualByComparingTo("140")
        assertThat(stored(itemId).targetHitOn).isEqualTo(dateUtils.date)
    }

    @Test
    fun `should rebase the target across a split between target date and close`() {
        val stock = asset("WLASPLIT")
        close(stock, 11, "100")
        val itemId = watch(stock, addedPrice = 100, targetPrice = 120)
        backdateTarget(itemId, 10)
        close(stock, 6, "100")
        close(stock, 5, "26", split = "4")
        close(stock, 1, "31")

        alertService.checkTargets()

        // 120 on the pre-split basis is 30 on the close's basis; 31 crosses it.
        val alert = alertsFor(itemId).single()
        assertThat(alert.path("targetPrice").decimalValue()).isEqualByComparingTo("30")
        assertThat(alert.path("close").decimalValue()).isEqualByComparingTo("31")
        assertThat(stored(itemId).targetHitOn).isEqualTo(dateUtils.date.minusDays(1))
    }

    @Test
    fun `should show alert prices on todays basis after a later split`() {
        val stock = asset("WLALATER")
        close(stock, 11, "100")
        val itemId = watch(stock, addedPrice = 100, targetPrice = 120)
        backdateTarget(itemId, 10)
        close(stock, 6, "130")
        alertService.checkTargets()
        close(stock, 5, "65", split = "2")
        close(stock, 1, "66")

        val alert = alertsFor(itemId).single()

        assertThat(alert.path("targetPrice").decimalValue()).isEqualByComparingTo("60")
        assertThat(alert.path("close").decimalValue()).isEqualByComparingTo("65")
        assertThat(alert.path("priceDate").asString()).isEqualTo(dateUtils.date.minusDays(6).toString())
    }

    @Test
    fun `should keep checking other items when one fails`() {
        val broken = asset("WLABROKEN")
        val healthy = asset("WLAHEALTHY")
        close(broken, 1, "100")
        close(healthy, 1, "100")
        val brokenItem = watch(broken, addedPrice = 100, targetPrice = 120)
        val healthyItem = watch(healthy, addedPrice = 100, targetPrice = 120)
        close(broken, 0, "125")
        close(healthy, 0, "125")
        // Only the broken item's write fails; every other call reaches the real repository.
        doThrow(IllegalStateException("boom"))
            .whenever(alertRepository)
            .save(argThat<WatchlistAlert> { item.id == brokenItem })

        alertService.checkTargets()

        assertThat(alertsFor(healthyItem)).hasSize(1)
        assertThat(stored(healthyItem).targetHitOn).isEqualTo(dateUtils.date)
        assertThat(alertsFor(brokenItem)).isEmpty()
        assertThat(stored(brokenItem).targetHitOn).describedAs("still armed").isNull()
    }

    @Test
    fun `should delete an alert and return the remaining alerts`() {
        val first = asset("WLADEL1")
        val second = asset("WLADEL2")
        close(first, 1, "100")
        close(second, 1, "100")
        val firstItem = watch(first, addedPrice = 100, targetPrice = 120)
        val secondItem = watch(second, addedPrice = 100, targetPrice = 120)
        close(first, 0, "125")
        close(second, 0, "125")
        alertService.checkTargets()
        val alertId = alertsFor(firstItem).single().path("id").asString()

        val remaining = call(delete(ALERT, alertId), tokenA).path("data").toList()

        assertThat(remaining.map { it.path("id").asString() }).doesNotContain(alertId)
        assertThat(remaining.map { it.path("itemId").asString() }).contains(secondItem)
        assertThat(alertsFor(firstItem)).isEmpty()
    }

    @Test
    fun `should hide another users alerts and answer 404 on their delete`() {
        val stock = asset("WLAPRIVATE")
        close(stock, 1, "100")
        val itemId = watch(stock, addedPrice = 100, targetPrice = 120)
        close(stock, 0, "125")
        alertService.checkTargets()
        val alertId = alertsFor(itemId).single().path("id").asString()

        assertThat(alerts(tokenB).map { it.path("id").asString() }).doesNotContain(alertId)
        call(delete(ALERT, alertId), tokenB, status().isNotFound)

        assertThat(alertsFor(itemId)).hasSize(1)
    }

    @Test
    fun `should answer 404 for an unknown alert`() {
        call(delete(ALERT, "no-such-alert"), tokenA, status().isNotFound)
    }

    @Test
    fun `should drop alerts when the item is deleted`() {
        val stock = asset("WLAITEMGONE")
        close(stock, 1, "100")
        val itemId = watch(stock, addedPrice = 100, targetPrice = 120)
        close(stock, 0, "125")
        alertService.checkTargets()

        call(delete(ITEM, itemId), tokenA)

        assertThat(alertsFor(itemId)).isEmpty()
    }

    @Test
    fun `should drop alerts when the asset is deleted`() {
        val stock = asset("WLAASSETGONE")
        close(stock, 1, "100")
        val itemId = watch(stock, addedPrice = 100, targetPrice = 120)
        close(stock, 0, "125")
        alertService.checkTargets()

        assetService.deleteAsset(stock.id)

        assertThat(alertsFor(itemId)).isEmpty()
        assertThat(alertRepository.findAll().filter { it.asset.id == stock.id }).isEmpty()
    }

    private fun markHit(
        itemId: String,
        targetOn: LocalDate,
        price: BigDecimal
    ): Int =
        requireNotNull(
            TransactionTemplate(transactionManager).execute {
                watchlistItemRepository.markHit(itemId, targetOn, price, dateUtils.date)
            }
        )

    /** Fixture: a target whose side V44 backfilled without a split rebase (V45 flags it). */
    private fun backfilledSide(
        itemId: String,
        direction: TargetDirection
    ) {
        watchlistItemRepository.save(stored(itemId).copy(targetDirection = direction, directionUnverified = true))
    }

    @Test
    fun `should re-derive a backfilled side across a split without alerting`() {
        val stock = asset("WLAREDERIVE")
        close(stock, 11, "110")
        val itemId = watch(stock, addedPrice = 110, targetPrice = 100)
        backdateTarget(itemId, 10)
        close(stock, 5, "27", split = "4")
        // V44 compared 100 (pre-split basis) with 27 and armed it ABOVE; on one basis it is 25.
        backfilledSide(itemId, TargetDirection.ABOVE)

        alertService.checkTargets()

        assertThat(alertsFor(itemId)).isEmpty()
        assertThat(stored(itemId).targetDirection).isEqualTo(TargetDirection.BELOW)
        assertThat(stored(itemId).directionUnverified).isFalse()
    }

    @Test
    fun `should alert on a re-derived side once the close crosses it`() {
        val stock = asset("WLAREDERIVED")
        close(stock, 11, "110")
        val itemId = watch(stock, addedPrice = 110, targetPrice = 100)
        backdateTarget(itemId, 10)
        close(stock, 5, "27", split = "4")
        backfilledSide(itemId, TargetDirection.ABOVE)
        alertService.checkTargets()
        close(stock, 1, "24")

        alertService.checkTargets()

        val alert = alertsFor(itemId).single()
        assertThat(alert.path("direction").asString()).isEqualTo("BELOW")
        assertThat(alert.path("targetPrice").decimalValue()).isEqualByComparingTo("25")
    }

    @Test
    fun `should clear the backfill flag when a target is set`() {
        val stock = asset("WLAREFLAG")
        close(stock, 1, "100")
        val itemId = watch(stock, addedPrice = 100, targetPrice = 120)
        backfilledSide(itemId, TargetDirection.ABOVE)

        retarget(itemId, mapOf("targetPrice" to 90))

        assertThat(stored(itemId).directionUnverified).isFalse()
        assertThat(stored(itemId).targetDirection).isEqualTo(TargetDirection.BELOW)
    }

    @Test
    fun `should stamp a target hit only for the first check that reaches it`() {
        val stock = asset("WLAMARK")
        close(stock, 1, "100")
        val itemId = watch(stock, addedPrice = 100, targetPrice = 120)
        val target = stored(itemId)
        val targetOn = requireNotNull(target.targetOn)
        val price = requireNotNull(target.targetPrice)

        val first = markHit(itemId, targetOn, price)
        val second = markHit(itemId, targetOn, price)

        assertThat(first).isEqualTo(1)
        assertThat(second).describedAs("already stamped").isZero()
        assertThat(stored(itemId).targetHitOn).isEqualTo(dateUtils.date)
    }

    @Test
    fun `should not stamp a target that was edited since the check read it`() {
        val stock = asset("WLAMARKEDIT")
        close(stock, 1, "100")
        val itemId = watch(stock, addedPrice = 100, targetPrice = 120)
        val targetOn = requireNotNull(stored(itemId).targetOn)
        retarget(itemId, mapOf("targetPrice" to 140))

        val stamped = markHit(itemId, targetOn, BigDecimal("120"))

        assertThat(stamped).isZero()
        assertThat(stored(itemId).targetHitOn).isNull()
    }

    companion object {
        const val ROOT = "/watchlist"
        const val ITEMS = "$ROOT/items"
        const val ITEM = "$ROOT/items/{itemId}"
        const val ALERTS = "$ROOT/alerts"
        const val ALERT = "$ROOT/alerts/{alertId}"
    }
}