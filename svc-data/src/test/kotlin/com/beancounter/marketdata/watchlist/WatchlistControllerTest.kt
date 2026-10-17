package com.beancounter.marketdata.watchlist

import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetRepository
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.providers.MarketDataRepo
import com.beancounter.marketdata.providers.alpha.AlphaPriceService
import com.beancounter.marketdata.registration.SystemUserRepository
import com.beancounter.marketdata.utils.RegistrationUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultMatcher
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.JsonNode
import java.math.BigDecimal

/**
 * Watchlists: owned, named lists of assets with no quantity. Exercised end to end through
 * MockMvc against real H2 state.
 */
@SpringMvcDbTest
class WatchlistControllerTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    // The add path prices through the provider-aware pipeline; stub the NASDAQ provider so
    // no test calls out. Closes it "returns" are keyed by asset code.
    @MockitoBean
    private lateinit var alphaPriceService: AlphaPriceService

    @Autowired
    private lateinit var cacheManager: CacheManager

    private val providerCloses = mutableMapOf<String, BigDecimal>()

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var assetRepository: AssetRepository

    @Autowired
    private lateinit var marketDataRepo: MarketDataRepo

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var watchlistItemRepository: WatchlistItemRepository

    @Autowired
    private lateinit var dateUtils: DateUtils

    @Autowired
    private lateinit var systemUserRepository: SystemUserRepository

    private lateinit var tokenA: Jwt
    private lateinit var tokenB: Jwt

    @Autowired
    fun setTokens(mockAuthConfig: MockAuthConfig) {
        tokenA =
            RegistrationUtils.registerUser(mockMvc, mockAuthConfig.getUserToken(SystemUser("wl-user-a", "wl-user-a")))
        tokenB =
            RegistrationUtils.registerUser(mockMvc, mockAuthConfig.getUserToken(SystemUser("wl-user-b", "wl-user-b")))
    }

    @BeforeEach
    fun stubProvider() {
        // MdFactory caches provider resolution; clear it so the mock is resolved.
        cacheManager.getCache("provider")?.clear()
        whenever(alphaPriceService.getId()).thenReturn(AlphaPriceService.ID)
        whenever(alphaPriceService.isMarketSupported(any())).thenReturn(true)
        whenever(alphaPriceService.isApiSupported()).thenReturn(true)
        whenever(alphaPriceService.getDate(any(), any())).thenReturn(dateUtils.date)
        whenever(alphaPriceService.getMarketData(any())).thenAnswer { invocation ->
            val request = invocation.getArgument<PriceRequest>(0)
            request.assets.mapNotNull { priceAsset ->
                val resolved = priceAsset.resolvedAsset ?: return@mapNotNull null
                providerCloses[resolved.code]?.let {
                    MarketData(asset = resolved, priceDate = dateUtils.date, close = it)
                }
            }
        }
    }

    private fun asset(code: String): Asset =
        assetRepository.save(
            Asset(
                code = code,
                id = KeyGenUtils().id,
                market = NASDAQ,
                marketCode = NASDAQ.code
            )
        )

    private fun price(
        asset: Asset,
        close: String,
        previousClose: String
    ) {
        val closeValue = BigDecimal(close)
        val prevValue = BigDecimal(previousClose)
        val change = closeValue.subtract(prevValue)
        marketDataRepo.save(
            MarketData(
                asset = asset,
                priceDate = dateUtils.date.minusDays(1),
                close = closeValue,
                previousClose = prevValue,
                change = change,
                changePercent = change.divide(prevValue, 6, java.math.RoundingMode.HALF_UP)
            )
        )
    }

    private fun call(
        request: MockHttpServletRequestBuilder,
        token: Jwt,
        expect: ResultMatcher,
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

    private fun create(
        code: String,
        token: Jwt = tokenA
    ): String =
        call(
            post(ROOT),
            token,
            status().isOk,
            mapOf("code" to code, "name" to "$code name")
        ).path("data").path("id").asString()

    private fun addItem(
        watchlistId: String,
        body: Map<String, Any?>,
        token: Jwt = tokenA,
        expect: ResultMatcher = status().isOk
    ): JsonNode = call(post("$ROOT/{id}/items", watchlistId), token, expect, body)

    private fun detail(
        watchlistId: String,
        token: Jwt = tokenA
    ): JsonNode = call(get("$ROOT/{id}", watchlistId), token, status().isOk).path("data")

    @Test
    fun `should create and list watchlists with item counts`() {
        val id = create("WL-LIST")
        addItem(id, mapOf("assetId" to asset("WLLIST").id, "addedPrice" to 10))

        val lists = call(get(ROOT), tokenA, status().isOk).path("data")

        val mine = lists.first { it.path("id").asString() == id }
        assertThat(mine.path("code").asString()).isEqualTo("WL-LIST")
        assertThat(mine.path("name").asString()).isEqualTo("WL-LIST name")
        assertThat(mine.path("itemCount").asInt()).isEqualTo(1)
    }

    @Test
    fun `should return 409 when watchlist code already exists for owner`() {
        create("WL-DUP")

        call(post(ROOT), tokenA, status().isConflict, mapOf("code" to "wl-dup", "name" to "again"))
    }

    @Test
    fun `should allow the same code for different owners`() {
        create("WL-SHARED-CODE")

        call(post(ROOT), tokenB, status().isOk, mapOf("code" to "WL-SHARED-CODE", "name" to "B"))
    }

    @Test
    fun `should stamp the provider close when adding a never priced asset`() {
        val id = create("WL-STAMP")
        val stock = asset("WLSTAMP")
        providerCloses[stock.code] = BigDecimal("50.25")

        val item = addItem(id, mapOf("assetId" to stock.id)).path("data")

        assertThat(item.path("addedPrice").decimalValue()).isEqualByComparingTo("50.25")
        assertThat(item.path("price").decimalValue()).isEqualByComparingTo("50.25")
        assertThat(item.path("priceDate").asString()).isEqualTo(dateUtils.date.toString())
        assertThat(item.path("changeSinceAdded").decimalValue()).isEqualByComparingTo("0")
        assertThat(item.path("addedOn").asString()).isEqualTo(dateUtils.date.toString())
        assertThat(item.path("targetPrice").isNull).isTrue()
    }

    @Test
    fun `should stamp the stored close when the provider has no price for the market date`() {
        val id = create("WL-STORED")
        val stock = asset("WLSTORED")
        price(stock, "48.10", "48.00")

        val item = addItem(id, mapOf("assetId" to stock.id)).path("data")

        assertThat(item.path("addedPrice").decimalValue()).isEqualByComparingTo("48.10")
    }

    @Test
    fun `should use caller supplied added price over latest close`() {
        val id = create("WL-OVERRIDE")
        val stock = asset("WLOVERRIDE")
        price(stock, "50.25", "50.00")

        val item = addItem(id, mapOf("assetId" to stock.id, "addedPrice" to 42)).path("data")

        assertThat(item.path("addedPrice").decimalValue()).isEqualByComparingTo("42")
    }

    @Test
    fun `should return 400 when no price exists and none supplied`() {
        val id = create("WL-NOPRICE")

        val problem = addItem(id, mapOf("assetId" to asset("WLNOPRICE").id), expect = status().isBadRequest)

        assertThat(problem.path("detail").asString()).contains("addedPrice")
    }

    @Test
    fun `should return 409 when asset already on watchlist`() {
        val id = create("WL-ITEM-DUP")
        val stock = asset("WLITEMDUP")
        addItem(id, mapOf("assetId" to stock.id, "addedPrice" to 1))

        addItem(id, mapOf("assetId" to stock.id, "addedPrice" to 2), expect = status().isConflict)
    }

    @Test
    fun `should compute changes since added and to target`() {
        val id = create("WL-CALC")
        val stock = asset("WLCALC")
        price(stock, "120", "100")
        addItem(id, mapOf("assetId" to stock.id, "addedPrice" to 100, "targetPrice" to 150))

        val watchlist = detail(id)
        val item = watchlist.path("items").first()

        assertThat(watchlist.path("code").asString()).isEqualTo("WL-CALC")
        assertThat(item.path("asset").path("id").asString()).isEqualTo(stock.id)
        assertThat(item.path("currency").path("code").asString()).isEqualTo("USD")
        assertThat(item.path("price").decimalValue()).isEqualByComparingTo("120")
        assertThat(item.path("priceDate").asString()).isEqualTo(dateUtils.date.minusDays(1).toString())
        assertThat(item.path("changeOnDay").decimalValue()).isEqualByComparingTo("20")
        assertThat(item.path("changeOnDayPercent").decimalValue()).isEqualByComparingTo("0.2")
        assertThat(item.path("changeSinceAdded").decimalValue()).isEqualByComparingTo("20")
        assertThat(item.path("changeSinceAddedPercent").decimalValue()).isEqualByComparingTo("0.2")
        assertThat(item.path("toTarget").decimalValue()).isEqualByComparingTo("0.25")
    }

    @Test
    fun `should return null price fields when asset has no price`() {
        val id = create("WL-UNPRICED")
        addItem(id, mapOf("assetId" to asset("WLUNPRICED").id, "addedPrice" to 10, "targetPrice" to 12))

        val item = detail(id).path("items").first()

        assertThat(item.path("addedPrice").decimalValue()).isEqualByComparingTo("10")
        listOf(
            "price",
            "priceDate",
            "changeOnDay",
            "changeOnDayPercent",
            "changeSinceAdded",
            "changeSinceAddedPercent",
            "toTarget"
        ).forEach { field ->
            assertThat(item.path(field).isNull).describedAs(field).isTrue()
        }
    }

    @Test
    fun `should rename watchlist`() {
        val id = create("WL-RENAME")

        call(patch("$ROOT/{id}", id), tokenA, status().isOk, mapOf("name" to "Renamed"))

        val watchlist = detail(id)
        assertThat(watchlist.path("name").asString()).isEqualTo("Renamed")
        assertThat(watchlist.path("code").asString()).isEqualTo("WL-RENAME")
    }

    @Test
    fun `should return 409 when renaming to a code the owner already uses`() {
        create("WL-TAKEN")
        val id = create("WL-MOVER")

        call(patch("$ROOT/{id}", id), tokenA, status().isConflict, mapOf("code" to "WL-TAKEN"))
    }

    @Test
    fun `should edit item target and clear target`() {
        val id = create("WL-EDIT")
        val itemId =
            addItem(
                id,
                mapOf("assetId" to asset("WLEDIT").id, "addedPrice" to 10)
            ).path("data").path("id").asString()

        val edited =
            call(
                patch("$ROOT/{id}/items/{itemId}", id, itemId),
                tokenA,
                status().isOk,
                mapOf("addedPrice" to 11, "targetPrice" to 15)
            ).path("data")
        assertThat(edited.path("addedPrice").decimalValue()).isEqualByComparingTo("11")
        assertThat(edited.path("targetPrice").decimalValue()).isEqualByComparingTo("15")

        val cleared =
            call(
                patch("$ROOT/{id}/items/{itemId}", id, itemId),
                tokenA,
                status().isOk,
                mapOf("clearTarget" to true)
            ).path("data")
        assertThat(cleared.path("targetPrice").isNull).isTrue()
        assertThat(cleared.path("addedPrice").decimalValue()).isEqualByComparingTo("11")
    }

    @Test
    fun `should return 400 when clearing and setting target together`() {
        val id = create("WL-AMBIGUOUS")
        val itemId =
            addItem(
                id,
                mapOf("assetId" to asset("WLAMBIG").id, "addedPrice" to 10)
            ).path("data").path("id").asString()

        call(
            patch("$ROOT/{id}/items/{itemId}", id, itemId),
            tokenA,
            status().isBadRequest,
            mapOf("clearTarget" to true, "targetPrice" to 15)
        )
    }

    @Test
    fun `should delete item`() {
        val id = create("WL-DEL-ITEM")
        val itemId =
            addItem(
                id,
                mapOf("assetId" to asset("WLDELITEM").id, "addedPrice" to 10)
            ).path("data").path("id").asString()

        call(delete("$ROOT/{id}/items/{itemId}", id, itemId), tokenA, status().isOk)

        assertThat(detail(id).path("items").size()).isEqualTo(0)
    }

    @Test
    fun `should delete watchlist with its items`() {
        val id = create("WL-DEL")
        addItem(id, mapOf("assetId" to asset("WLDEL").id, "addedPrice" to 10))

        call(delete("$ROOT/{id}", id), tokenA, status().isOk)

        call(get("$ROOT/{id}", id), tokenA, status().isNotFound)
    }

    @Test
    fun `should return 404 when watchlist not found`() {
        call(get("$ROOT/{id}", "no-such-watchlist"), tokenA, status().isNotFound)
    }

    @Test
    fun `should return 404 for another users watchlist on every endpoint`() {
        val id = create("WL-PRIVATE")
        val stock = asset("WLPRIVATE")
        val itemId = addItem(id, mapOf("assetId" to stock.id, "addedPrice" to 10)).path("data").path("id").asString()
        val notFound = status().isNotFound

        call(get("$ROOT/{id}", id), tokenB, notFound)
        call(patch("$ROOT/{id}", id), tokenB, notFound, mapOf("name" to "hijack"))
        call(delete("$ROOT/{id}", id), tokenB, notFound)
        addItem(id, mapOf("assetId" to asset("WLPRIVATE2").id, "addedPrice" to 1), tokenB, notFound)
        call(patch("$ROOT/{id}/items/{itemId}", id, itemId), tokenB, notFound, mapOf("targetPrice" to 99))
        call(delete("$ROOT/{id}/items/{itemId}", id, itemId), tokenB, notFound)

        val lists = call(get(ROOT), tokenB, status().isOk).path("data")
        assertThat(lists.map { it.path("id").asString() }).doesNotContain(id)
        val untouched = detail(id)
        assertThat(untouched.path("name").asString()).isEqualTo("WL-PRIVATE name")
        assertThat(untouched.path("items").size()).isEqualTo(1)
        assertThat(
            untouched
                .path("items")
                .first()
                .path("targetPrice")
                .isNull
        ).isTrue()
    }

    @Test
    fun `should return 404 when item belongs to a different watchlist of the same owner`() {
        val first = create("WL-FIRST")
        val second = create("WL-SECOND")
        val itemId =
            addItem(
                first,
                mapOf("assetId" to asset("WLFIRST").id, "addedPrice" to 10)
            ).path("data").path("id").asString()

        call(delete("$ROOT/{id}/items/{itemId}", second, itemId), tokenA, status().isNotFound)

        assertThat(detail(first).path("items").size()).isEqualTo(1)
    }

    @Test
    fun `should return 404 when adding another users private asset`() {
        val id = create("WL-FOREIGN-ASSET")
        val foreign =
            assetRepository.save(
                Asset(
                    code = "WLFOREIGN",
                    id = KeyGenUtils().id,
                    market = NASDAQ,
                    marketCode = NASDAQ.code,
                    systemUser = systemUserRepository.findByEmail("wl-user-b").orElseThrow()
                )
            )

        addItem(id, mapOf("assetId" to foreign.id, "addedPrice" to 1), expect = status().isNotFound)
    }

    @Test
    fun `should drop watchlist items when the asset is deleted`() {
        val id = create("WL-ASSET-GONE")
        val stock = asset("WLASSETGONE")
        addItem(id, mapOf("assetId" to stock.id, "addedPrice" to 10))

        assetService.deleteAsset(stock.id)

        assertThat(detail(id).path("items").size()).isEqualTo(0)
    }

    private fun stored(
        asset: Asset,
        daysAgo: Long,
        close: String,
        split: String = "1"
    ) {
        marketDataRepo.save(
            MarketData(
                asset = asset,
                priceDate = dateUtils.date.minusDays(daysAgo),
                close = BigDecimal(close),
                split = BigDecimal(split)
            )
        )
    }

    /** Fixture: an item added (with its target) [daysAgo] days ago. */
    private fun backdate(
        itemId: String,
        daysAgo: Long
    ) {
        val item = watchlistItemRepository.findById(itemId).orElseThrow()
        watchlistItemRepository.save(
            item.copy(
                addedOn = dateUtils.date.minusDays(daysAgo),
                targetOn = item.targetOn?.let { dateUtils.date.minusDays(daysAgo) }
            )
        )
    }

    @Test
    fun `should rebase added and target prices across a split after addedOn`() {
        val id = create("WL-SPLIT")
        val stock = asset("WLSPLIT")
        val itemId =
            addItem(id, mapOf("assetId" to stock.id, "addedPrice" to 100, "targetPrice" to 120))
                .path("data")
                .path("id")
                .asString()
        backdate(itemId, 10)
        stored(stock, 6, "100")
        stored(stock, 5, "25", split = "4")
        stored(stock, 4, "25.5", split = "4")
        stored(stock, 1, "26")

        val item = detail(id).path("items").first()

        assertThat(item.path("price").decimalValue()).isEqualByComparingTo("26")
        assertThat(item.path("addedPrice").decimalValue()).isEqualByComparingTo("25")
        assertThat(item.path("changeSinceAdded").decimalValue()).isEqualByComparingTo("1")
        assertThat(item.path("changeSinceAddedPercent").decimalValue()).isEqualByComparingTo("0.04")
        assertThat(item.path("targetPrice").decimalValue()).isEqualByComparingTo("30")
        assertThat(item.path("toTarget").decimalValue()).isEqualByComparingTo("0.153846")
    }

    @Test
    fun `should read edited prices on todays basis after a split`() {
        val id = create("WL-SPLIT-EDIT")
        val stock = asset("WLSPLITEDIT")
        val itemId =
            addItem(id, mapOf("assetId" to stock.id, "addedPrice" to 100)).path("data").path("id").asString()
        backdate(itemId, 10)
        stored(stock, 5, "25", split = "4")
        stored(stock, 1, "26")

        val edited =
            call(
                patch("$ROOT/{id}/items/{itemId}", id, itemId),
                tokenA,
                status().isOk,
                mapOf("addedPrice" to 24, "targetPrice" to 40)
            ).path("data")

        assertThat(edited.path("addedPrice").decimalValue()).isEqualByComparingTo("24")
        assertThat(edited.path("targetPrice").decimalValue()).isEqualByComparingTo("40")
        assertThat(watchlistItemRepository.findById(itemId).orElseThrow().addedPrice)
            .describedAs("stored on the addedOn share basis")
            .isEqualByComparingTo("96")
    }

    @Test
    fun `should report the day change on add that a later read reports`() {
        val id = create("WL-DAYCHANGE")
        val stock = asset("WLDAYCHANGE")
        stored(stock, 1, "100")
        providerCloses[stock.code] = BigDecimal("110")

        val added = addItem(id, mapOf("assetId" to stock.id)).path("data")

        assertThat(added.path("changeOnDay").decimalValue()).isEqualByComparingTo("10")
        assertThat(added.path("changeOnDayPercent").decimalValue()).isEqualByComparingTo("0.1")

        // The fetched price persists asynchronously; wait for the read path to see it.
        var read = detail(id).path("items").first()
        repeat(POLL_ATTEMPTS) {
            if (read.path("priceDate").asString() != dateUtils.date.toString()) {
                Thread.sleep(POLL_MS)
                read = detail(id).path("items").first()
            }
        }
        assertThat(read.path("priceDate").asString()).isEqualTo(dateUtils.date.toString())
        assertThat(
            read.path("changeOnDay").decimalValue()
        ).isEqualByComparingTo(added.path("changeOnDay").decimalValue())
        assertThat(read.path("changeOnDayPercent").decimalValue())
            .isEqualByComparingTo(added.path("changeOnDayPercent").decimalValue())
    }

    @Test
    fun `should return 400 when code or name exceeds 255 characters`() {
        val tooLong = "X".repeat(256)

        call(post(ROOT), tokenA, status().isBadRequest, mapOf("code" to tooLong, "name" to "ok"))
        call(post(ROOT), tokenA, status().isBadRequest, mapOf("code" to "WL-LONG", "name" to tooLong))
        val id = create("WL-LONG-RENAME")
        call(patch("$ROOT/{id}", id), tokenA, status().isBadRequest, mapOf("name" to tooLong))
    }

    companion object {
        private const val POLL_ATTEMPTS = 50
        private const val POLL_MS = 200L
        const val ROOT = "/watchlists"
    }
}