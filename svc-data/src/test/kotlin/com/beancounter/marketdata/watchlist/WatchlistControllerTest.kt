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
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.cache.CacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
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
import org.springframework.transaction.support.TransactionSynchronizationManager
import tools.jackson.databind.JsonNode
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The caller's watchlist (one per user): assets with no quantity. Exercised end to end through
 * MockMvc against real H2 state. Users A and B persist across tests, so each test works with
 * its own assets and finds its items by id.
 */
@SpringMvcDbTest
@Import(WatchlistControllerTest.TradingHours::class)
class WatchlistControllerTest {
    /** Live quotes are only asked for in session: pin the clock to a weekday trading hour. */
    @TestConfiguration
    class TradingHours {
        @Bean
        fun clock(): Clock = Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ZoneOffset.UTC)
    }

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    // The add path prices through the provider-aware pipeline; stub the NASDAQ provider so
    // no test calls out. Closes it "returns" are keyed by asset code.
    @MockitoBean
    private lateinit var alphaPriceService: AlphaPriceService

    @Autowired
    private lateinit var cacheManager: CacheManager

    private val providerCloses = mutableMapOf<String, BigDecimal>()
    private val providerSplits = mutableMapOf<String, BigDecimal>()

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

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

    @BeforeEach
    fun setUp() {
        tokenA = register("wl-user-a")
        tokenB = register("wl-user-b")
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
                    MarketData(
                        asset = resolved,
                        priceDate = dateUtils.date,
                        close = it,
                        split = providerSplits[resolved.code] ?: BigDecimal.ONE
                    )
                }
            }
        }
    }

    private fun register(user: String): Jwt =
        RegistrationUtils.registerUser(mockMvc, mockAuthConfig.getUserToken(SystemUser(user, user)))

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
                changePercent = change.divide(prevValue, 6, RoundingMode.HALF_UP)
            )
        )
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

    private fun addItem(
        body: Map<String, Any?>,
        token: Jwt = tokenA,
        expect: ResultMatcher = status().isOk
    ): JsonNode = call(post(ITEMS), token, expect, body)

    private fun addedId(body: Map<String, Any?>): String = addItem(body).path("data").path("id").asString()

    private fun items(token: Jwt = tokenA): List<JsonNode> =
        call(get(ROOT), token, status().isOk).path("data").path("items").toList()

    private fun item(
        itemId: String,
        token: Jwt = tokenA
    ): JsonNode = items(token).first { it.path("id").asString() == itemId }

    private fun itemIds(token: Jwt = tokenA): List<String> = items(token).map { it.path("id").asString() }

    /** Fixture: an item added (with its target) [daysAgo] days ago. */
    private fun backdate(
        itemId: String,
        daysAgo: Long
    ) {
        val stored = watchlistItemRepository.findById(itemId).orElseThrow()
        watchlistItemRepository.save(
            stored.copy(
                addedOn = dateUtils.date.minusDays(daysAgo),
                targetOn = stored.targetOn?.let { dateUtils.date.minusDays(daysAgo) }
            )
        )
    }

    @Test
    fun `should return an empty watchlist for a user who watches nothing`() {
        val token = register("wl-user-empty")

        val watchlist = call(get(ROOT), token, status().isOk).path("data")

        assertThat(watchlist.path("items").isArray).isTrue()
        assertThat(watchlist.path("items").size()).isEqualTo(0)
    }

    @Test
    fun `should stamp the provider close when adding a never priced asset`() {
        val stock = asset("WLSTAMP")
        providerCloses[stock.code] = BigDecimal("50.25")

        val added = addItem(mapOf("assetId" to stock.id)).path("data")

        assertThat(added.path("addedPrice").decimalValue()).isEqualByComparingTo("50.25")
        assertThat(added.path("price").decimalValue()).isEqualByComparingTo("50.25")
        assertThat(added.path("priceDate").asString()).isEqualTo(dateUtils.date.toString())
        assertThat(added.path("changeSinceAdded").decimalValue()).isEqualByComparingTo("0")
        assertThat(added.path("addedOn").asString()).isEqualTo(dateUtils.date.toString())
        assertThat(added.path("targetPrice").isNull).isTrue()
        assertThat(itemIds()).contains(added.path("id").asString())
    }

    @Test
    fun `should stamp the stored close when the provider has no price for the market date`() {
        val stock = asset("WLSTORED")
        price(stock, "48.10", "48.00")

        val added = addItem(mapOf("assetId" to stock.id)).path("data")

        assertThat(added.path("addedPrice").decimalValue()).isEqualByComparingTo("48.10")
    }

    @Test
    fun `should use caller supplied added price over latest close`() {
        val stock = asset("WLOVERRIDE")
        price(stock, "50.25", "50.00")

        val added = addItem(mapOf("assetId" to stock.id, "addedPrice" to 42)).path("data")

        assertThat(added.path("addedPrice").decimalValue()).isEqualByComparingTo("42")
    }

    @Test
    fun `should return 400 when no price exists and none supplied`() {
        val problem = addItem(mapOf("assetId" to asset("WLNOPRICE").id), expect = status().isBadRequest)

        assertThat(problem.path("detail").asString()).contains("addedPrice")
    }

    @Test
    fun `should return 409 when asset already watched`() {
        val stock = asset("WLITEMDUP")
        addItem(mapOf("assetId" to stock.id, "addedPrice" to 1))

        addItem(mapOf("assetId" to stock.id, "addedPrice" to 2), expect = status().isConflict)
    }

    @Test
    fun `should let different users watch the same asset`() {
        val stock = asset("WLSHARED")
        addItem(mapOf("assetId" to stock.id, "addedPrice" to 1))

        addItem(mapOf("assetId" to stock.id, "addedPrice" to 2), token = tokenB)
    }

    @Test
    fun `should compute changes since added and to target`() {
        val stock = asset("WLCALC")
        price(stock, "120", "100")
        val itemId = addedId(mapOf("assetId" to stock.id, "addedPrice" to 100, "targetPrice" to 150))

        val read = item(itemId)

        assertThat(read.path("asset").path("id").asString()).isEqualTo(stock.id)
        assertThat(read.path("currency").path("code").asString()).isEqualTo("USD")
        assertThat(read.path("price").decimalValue()).isEqualByComparingTo("120")
        assertThat(read.path("priceDate").asString()).isEqualTo(dateUtils.date.minusDays(1).toString())
        assertThat(read.path("changeOnDay").decimalValue()).isEqualByComparingTo("20")
        assertThat(read.path("changeOnDayPercent").decimalValue()).isEqualByComparingTo("0.2")
        assertThat(read.path("changeSinceAdded").decimalValue()).isEqualByComparingTo("20")
        assertThat(read.path("changeSinceAddedPercent").decimalValue()).isEqualByComparingTo("0.2")
        assertThat(read.path("toTarget").decimalValue()).isEqualByComparingTo("0.25")
    }

    @Test
    fun `should show the provider's live quote over the stored close`() {
        val stock = asset("WLLIVE")
        price(stock, "120", "100")
        whenever(alphaPriceService.getQuotes(any())).thenAnswer { invocation ->
            invocation
                .getArgument<Collection<Asset>>(0)
                .filter { it.code == "WLLIVE" }
                .associate {
                    it.id to
                        MarketData(
                            asset = it,
                            priceDate = dateUtils.date,
                            close = BigDecimal("125"),
                            previousClose = BigDecimal("120"),
                            change = BigDecimal("5"),
                            changePercent = BigDecimal("0.041667")
                        )
                }
        }
        val added = addItem(mapOf("assetId" to stock.id, "addedPrice" to 100, "targetPrice" to 150)).path("data")
        val itemId = added.path("id").asString()
        // Add and edit responses report the same live price a later read does.
        assertThat(added.path("price").decimalValue()).isEqualByComparingTo("125")
        val edited = call(patch(ITEM, itemId), tokenA, status().isOk, mapOf("targetPrice" to 150)).path("data")
        assertThat(edited.path("price").decimalValue()).isEqualByComparingTo("125")

        val read = item(itemId)

        assertThat(read.path("price").decimalValue()).isEqualByComparingTo("125")
        assertThat(read.path("priceDate").asString()).isEqualTo(dateUtils.date.toString())
        assertThat(read.path("changeOnDay").decimalValue()).isEqualByComparingTo("5")
        assertThat(read.path("changeOnDayPercent").decimalValue()).isEqualByComparingTo("0.041667")
        assertThat(read.path("changeSinceAdded").decimalValue()).isEqualByComparingTo("25")
        assertThat(read.path("changeSinceAddedPercent").decimalValue()).isEqualByComparingTo("0.25")
        assertThat(read.path("toTarget").decimalValue()).isEqualByComparingTo("0.2")
    }

    @Test
    fun `should keep the stored close when the live quote is older`() {
        val stock = asset("WLSTALE")
        price(stock, "120", "100")
        whenever(alphaPriceService.getQuotes(any())).thenAnswer { invocation ->
            invocation
                .getArgument<Collection<Asset>>(0)
                .filter { it.code == "WLSTALE" }
                .associate {
                    it.id to MarketData(asset = it, priceDate = dateUtils.date.minusDays(3), close = BigDecimal("90"))
                }
        }
        val itemId = addedId(mapOf("assetId" to stock.id, "addedPrice" to 100))

        val read = item(itemId)

        assertThat(read.path("price").decimalValue()).isEqualByComparingTo("120")
        assertThat(read.path("priceDate").asString()).isEqualTo(dateUtils.date.minusDays(1).toString())
    }

    /** Records, per provider live-quote call, whether a transaction was open around it. */
    private fun recordTransactionOnQuote(): List<Boolean> {
        val held = mutableListOf<Boolean>()
        whenever(alphaPriceService.getQuotes(any())).thenAnswer {
            held.add(TransactionSynchronizationManager.isActualTransactionActive())
            emptyMap<String, MarketData>()
        }
        return held
    }

    @Test
    fun `should not hold a transaction while the provider quotes an added item`() {
        val held = recordTransactionOnQuote()

        addItem(mapOf("assetId" to asset("WLTXADD").id, "addedPrice" to 10, "targetPrice" to 12))

        assertThat(held).containsExactly(false)
    }

    @Test
    fun `should not hold a transaction while the provider quotes an edited item`() {
        val stock = asset("WLTXEDIT")
        // Added at the provider close, so the add asks for no live quote and the edit's is not cached.
        providerCloses[stock.code] = BigDecimal("10")
        val itemId = addedId(mapOf("assetId" to stock.id))
        val held = recordTransactionOnQuote()

        call(patch(ITEM, itemId), tokenA, status().isOk, mapOf("targetPrice" to 15))

        assertThat(held).containsExactly(false)
    }

    @Test
    fun `should return null price fields when asset has no price`() {
        val itemId = addedId(mapOf("assetId" to asset("WLUNPRICED").id, "addedPrice" to 10, "targetPrice" to 12))

        val read = item(itemId)

        assertThat(read.path("addedPrice").decimalValue()).isEqualByComparingTo("10")
        listOf(
            "price",
            "priceDate",
            "changeOnDay",
            "changeOnDayPercent",
            "changeSinceAdded",
            "changeSinceAddedPercent",
            "toTarget"
        ).forEach { field ->
            assertThat(read.path(field).isNull).describedAs(field).isTrue()
        }
    }

    @Test
    fun `should edit item target and clear target`() {
        val itemId = addedId(mapOf("assetId" to asset("WLEDIT").id, "addedPrice" to 10))

        val edited =
            call(
                patch(ITEM, itemId),
                tokenA,
                status().isOk,
                mapOf("addedPrice" to 11, "targetPrice" to 15)
            ).path("data")
        assertThat(edited.path("addedPrice").decimalValue()).isEqualByComparingTo("11")
        assertThat(edited.path("targetPrice").decimalValue()).isEqualByComparingTo("15")

        val cleared = call(patch(ITEM, itemId), tokenA, status().isOk, mapOf("clearTarget" to true)).path("data")
        assertThat(cleared.path("targetPrice").isNull).isTrue()
        assertThat(cleared.path("addedPrice").decimalValue()).isEqualByComparingTo("11")
    }

    @Test
    fun `should return 400 when clearing and setting target together`() {
        val itemId = addedId(mapOf("assetId" to asset("WLAMBIG").id, "addedPrice" to 10))

        call(patch(ITEM, itemId), tokenA, status().isBadRequest, mapOf("clearTarget" to true, "targetPrice" to 15))
    }

    @Test
    fun `should delete item`() {
        val itemId = addedId(mapOf("assetId" to asset("WLDELITEM").id, "addedPrice" to 10))

        val deleted = call(delete(ITEM, itemId), tokenA, status().isOk)

        assertThat(deleted.path("deleted").asString()).isEqualTo(itemId)
        assertThat(itemIds()).doesNotContain(itemId)
    }

    @Test
    fun `should return 404 when item not found`() {
        call(delete(ITEM, "no-such-item"), tokenA, status().isNotFound)
    }

    @Test
    fun `should return 404 for another users item on every endpoint`() {
        val itemId = addedId(mapOf("assetId" to asset("WLPRIVATE").id, "addedPrice" to 10))
        val notFound = status().isNotFound

        call(patch(ITEM, itemId), tokenB, notFound, mapOf("targetPrice" to 99))
        call(delete(ITEM, itemId), tokenB, notFound)

        assertThat(itemIds(tokenB)).doesNotContain(itemId)
        val untouched = item(itemId)
        assertThat(untouched.path("targetPrice").isNull).isTrue()
    }

    @Test
    fun `should return 404 when adding another users private asset`() {
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

        addItem(mapOf("assetId" to foreign.id, "addedPrice" to 1), expect = status().isNotFound)
    }

    @Test
    fun `should drop watchlist items when the asset is deleted`() {
        val stock = asset("WLASSETGONE")
        val itemId = addedId(mapOf("assetId" to stock.id, "addedPrice" to 10))

        assetService.deleteAsset(stock.id)

        assertThat(itemIds()).doesNotContain(itemId)
    }

    @Test
    fun `should rebase added and target prices across a split after addedOn`() {
        val stock = asset("WLSPLIT")
        val itemId = addedId(mapOf("assetId" to stock.id, "addedPrice" to 100, "targetPrice" to 120))
        backdate(itemId, 10)
        stored(stock, 6, "100")
        stored(stock, 5, "25", split = "4")
        stored(stock, 4, "25.5", split = "4")
        stored(stock, 1, "26")

        val read = item(itemId)

        assertThat(read.path("price").decimalValue()).isEqualByComparingTo("26")
        assertThat(read.path("addedPrice").decimalValue()).isEqualByComparingTo("25")
        assertThat(read.path("changeSinceAdded").decimalValue()).isEqualByComparingTo("1")
        assertThat(read.path("changeSinceAddedPercent").decimalValue()).isEqualByComparingTo("0.04")
        assertThat(read.path("targetPrice").decimalValue()).isEqualByComparingTo("30")
        assertThat(read.path("toTarget").decimalValue()).isEqualByComparingTo("0.153846")
    }

    @Test
    fun `should read edited prices on todays basis after a split`() {
        val stock = asset("WLSPLITEDIT")
        val itemId = addedId(mapOf("assetId" to stock.id, "addedPrice" to 100))
        backdate(itemId, 10)
        stored(stock, 6, "100")
        stored(stock, 5, "25", split = "4")
        stored(stock, 1, "26")

        val edited =
            call(
                patch(ITEM, itemId),
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
    fun `should rebase each item by its own asset splits in a multi item read`() {
        val split = asset("WLMULTISPLIT")
        val flat = asset("WLMULTIFLAT")
        val splitItem = addedId(mapOf("assetId" to split.id, "addedPrice" to 100))
        val flatItem = addedId(mapOf("assetId" to flat.id, "addedPrice" to 50))
        backdate(splitItem, 10)
        backdate(flatItem, 10)
        stored(split, 6, "100")
        stored(split, 5, "25", split = "4")
        stored(split, 1, "26")
        stored(flat, 6, "50")
        stored(flat, 1, "55")

        val read = items().associateBy { it.path("id").asString() }

        assertThat(read.getValue(splitItem).path("addedPrice").decimalValue()).isEqualByComparingTo("25")
        assertThat(read.getValue(splitItem).path("changeSinceAddedPercent").decimalValue()).isEqualByComparingTo("0.04")
        assertThat(read.getValue(flatItem).path("addedPrice").decimalValue()).isEqualByComparingTo("50")
        assertThat(read.getValue(flatItem).path("changeSinceAddedPercent").decimalValue()).isEqualByComparingTo("0.1")
    }

    @Test
    fun `should detect a split at the start of the window from the row before it`() {
        val stock = asset("WLSPARSE")
        val itemId = addedId(mapOf("assetId" to stock.id, "addedPrice" to 100))
        backdate(itemId, 10)
        // Sparse history: the last row before the item was added is well before it.
        stored(stock, 40, "100")
        stored(stock, 5, "25", split = "4")
        stored(stock, 1, "26")

        assertThat(item(itemId).path("addedPrice").decimalValue()).isEqualByComparingTo("25")
    }

    @Test
    fun `should stamp the enriched close when the fetched ex-date close is unadjusted`() {
        val stock = asset("WLEXDATE")
        stored(stock, 1, "100")
        providerCloses[stock.code] = BigDecimal("100")
        providerSplits[stock.code] = BigDecimal("4")

        val added = addItem(mapOf("assetId" to stock.id)).path("data")

        assertThat(added.path("price").decimalValue()).isEqualByComparingTo("25")
        assertThat(added.path("addedPrice").decimalValue()).isEqualByComparingTo("25")
        assertThat(watchlistItemRepository.findById(added.path("id").asString()).orElseThrow().addedPrice)
            .isEqualByComparingTo("25")
    }

    @Test
    fun `should refuse a stored target without its entry date`() {
        val itemId = addedId(mapOf("assetId" to asset("WLTARGETON").id, "addedPrice" to 10))
        val stored = watchlistItemRepository.findById(itemId).orElseThrow()

        assertThatThrownBy {
            watchlistItemRepository.save(stored.copy(targetPrice = BigDecimal.TEN, targetOn = null))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `should report the day change on add that a later read reports`() {
        val stock = asset("WLDAYCHANGE")
        stored(stock, 1, "100")
        providerCloses[stock.code] = BigDecimal("110")

        val added = addItem(mapOf("assetId" to stock.id)).path("data")
        val itemId = added.path("id").asString()

        assertThat(added.path("changeOnDay").decimalValue()).isEqualByComparingTo("10")
        assertThat(added.path("changeOnDayPercent").decimalValue()).isEqualByComparingTo("0.1")

        // The fetched price persists asynchronously; wait for the read path to see it.
        var read = item(itemId)
        repeat(POLL_ATTEMPTS) {
            if (read.path("priceDate").asString() != dateUtils.date.toString()) {
                Thread.sleep(POLL_MS)
                read = item(itemId)
            }
        }
        assertThat(read.path("priceDate").asString()).isEqualTo(dateUtils.date.toString())
        assertThat(
            read.path("changeOnDay").decimalValue()
        ).isEqualByComparingTo(added.path("changeOnDay").decimalValue())
        assertThat(read.path("changeOnDayPercent").decimalValue())
            .isEqualByComparingTo(added.path("changeOnDayPercent").decimalValue())
    }

    companion object {
        private const val POLL_ATTEMPTS = 50
        private const val POLL_MS = 200L
        const val ROOT = "/watchlist"
        const val ITEMS = "$ROOT/items"
        const val ITEM = "$ROOT/items/{itemId}"
    }
}