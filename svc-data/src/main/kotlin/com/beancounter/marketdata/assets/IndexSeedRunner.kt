package com.beancounter.marketdata.assets

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.utils.AssetKeyUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

const val INDEX_MARKET_CODE = "INDEX"

/**
 * Creates pre-seeded index assets at startup. Idempotent: existing assets are
 * looked up via AssetService.create() which short-circuits to findLocally first.
 *
 * The auto-run on `ApplicationRunner` is gated by
 * `beancounter.indices.seed-on-startup` (default true). Tests that mock
 * CurrencyService or load a partial context set this to false and call
 * [seed] explicitly when they need the data.
 */
@Component
class IndexSeedRunner(
    private val indexConfig: IndexConfig,
    private val assetService: AssetService
) : ApplicationRunner {
    @Value("\${beancounter.indices.seed-on-startup:true}")
    private var seedOnStartup: Boolean = true

    override fun run(args: ApplicationArguments) {
        if (!seedOnStartup) {
            log.debug("Index pre-seed disabled by configuration")
            return
        }
        seed()
    }

    fun seed() {
        if (indexConfig.values.isEmpty()) {
            return
        }
        val inputs =
            indexConfig.values.associate { def ->
                val input =
                    AssetInput(
                        market = INDEX_MARKET_CODE,
                        code = def.code,
                        name = def.name,
                        currency = def.currency,
                        category = AssetCategory.INDEX
                    )
                AssetKeyUtils.toKey(input) to input
            }
        val response = assetService.handle(AssetRequest(inputs))

        // Defensive: contract / partial-context tests mock AssetService and
        // the mock returns null despite the non-null return type. Don't fail
        // startup over a missing seed (hence the deliberate null-safe access).
        @Suppress("UNNECESSARY_SAFE_CALL", "SENSELESS_COMPARISON")
        val count = response?.data?.size ?: 0
        log.info("Seeded {} index assets", count)
    }

    companion object {
        private val log = LoggerFactory.getLogger(IndexSeedRunner::class.java)
    }
}