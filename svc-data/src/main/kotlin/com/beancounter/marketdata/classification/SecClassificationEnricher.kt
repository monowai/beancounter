package com.beancounter.marketdata.classification

import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetClassification
import com.beancounter.common.model.AssetFundamentals
import com.beancounter.common.model.ClassificationLevel
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.providers.sec.SecFundamentalsParser
import com.beancounter.marketdata.providers.sec.SecProxy
import com.beancounter.marketdata.providers.sec.SecTickerResolver
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpStatusCodeException
import tools.jackson.databind.ObjectMapper

/**
 * Enriches US-listed equities from SEC EDGAR - keyless, public domain, so it keeps working when
 * the AlphaVantage key is on the free tier (where OVERVIEW is premium-gated and every refresh
 * ends `processed=0, rateLimited=1`).
 *
 * - Sector: the registrant's SIC code from `/submissions`, mapped by [SicSectorMapper].
 * - Industry: the SIC description verbatim.
 * - Fundamentals: one [AssetFundamentals] snapshot per asset from `/api/xbrl/companyfacts`,
 *   upserted in the same pass.
 *
 * [canEnrich] accepts only what this enricher can actually answer: an equity on a US exchange
 * whose ticker is in the SEC company index (the index is cached, so the lookup is cheap). ETFs
 * and unlisted tickers are declined rather than answered NO_DATA, so a chain of `sec,alpha`
 * hands them to the next enricher instead of parking them. Stored under the dedicated SEC
 * [com.beancounter.common.model.ClassificationStandard], like the other providers. HTTP 429 and
 * 403 (SEC's throttle response) and our own `sec` limiter tripping map to RATE_LIMITED so the
 * asset is retried first on the next run; any other HTTP or parse failure is FAILED.
 */
@Service
class SecClassificationEnricher(
    private val secProxy: SecProxy,
    private val tickerResolver: SecTickerResolver,
    private val sicSectorMapper: SicSectorMapper,
    private val fundamentalsParser: SecFundamentalsParser,
    private val classificationService: ClassificationService,
    private val fundamentalsRepository: AssetFundamentalsRepository,
    private val dateUtils: DateUtils = DateUtils(),
    private val objectMapper: ObjectMapper = BcJson.objectMapper
) : ClassificationEnricher {
    private val log = LoggerFactory.getLogger(SecClassificationEnricher::class.java)

    override fun canEnrich(asset: Asset): Boolean =
        isEquity(asset) && asset.market.code.uppercase() in SEC_MARKETS && tickerResolver.resolve(asset) != null

    override fun isEtf(asset: Asset): Boolean = ClassificationEnricher.categoryIsEtf(asset)

    override fun isEquity(asset: Asset): Boolean = ClassificationEnricher.categoryIsEquity(asset)

    override fun enrichClassification(asset: Asset): EnrichmentResult {
        if (!isEquity(asset)) {
            logNoData(asset, "SEC carries no sector weights for funds")
            return EnrichmentResult.NO_DATA
        }
        val cik = tickerResolver.resolve(asset)
        if (cik == null) {
            logNoData(asset, "ticker is not in the SEC company index")
            return EnrichmentResult.NO_DATA
        }
        return try {
            val result = classify(asset, cik)
            snapshotFundamentals(asset, cik)
            result
        } catch (e: RequestNotPermitted) {
            log.warn("SEC rate limiter declined {}:{} - {}", asset.market.code, asset.code, e.message)
            EnrichmentResult.RATE_LIMITED
        } catch (e: HttpStatusCodeException) {
            if (e.statusCode in THROTTLE_STATUSES) {
                log.warn(
                    "SEC throttled ({}) while enriching {}:{}",
                    e.statusCode.value(),
                    asset.market.code,
                    asset.code
                )
                EnrichmentResult.RATE_LIMITED
            } else {
                log.warn("SEC request failed for {}:{} - {}", asset.market.code, asset.code, e.message)
                EnrichmentResult.FAILED
            }
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.warn("Failed to enrich classification for {}:{} - {}", asset.market.code, asset.code, e.message)
            EnrichmentResult.FAILED
        }
    }

    private fun classify(
        asset: Asset,
        cik: String
    ): EnrichmentResult {
        val submissions = objectMapper.readTree(secProxy.getSubmissions(cik))
        val sic = submissions.path("sic").asString()
        val sector = sicSectorMapper.toSector(sic)
        if (sector == null) {
            logNoData(asset, "SIC '$sic' has no sector mapping")
            return EnrichmentResult.NO_DATA
        }
        val industry = submissions.path("sicDescription").asString()
        val standard = classificationService.getSecStandard()

        val sectorItem =
            classificationService.getOrCreateItem(
                standard = standard,
                level = ClassificationLevel.SECTOR,
                rawCode = sector
            )
        classificationService.classifyAsset(
            asset = asset,
            standard = standard,
            item = sectorItem,
            level = ClassificationLevel.SECTOR,
            source = AssetClassification.SOURCE_SEC
        )

        if (industry.isNotBlank()) {
            val industryItem =
                classificationService.getOrCreateItem(
                    standard = standard,
                    level = ClassificationLevel.INDUSTRY,
                    name = industry,
                    parent = sectorItem
                )
            classificationService.classifyAsset(
                asset = asset,
                standard = standard,
                item = industryItem,
                level = ClassificationLevel.INDUSTRY,
                source = AssetClassification.SOURCE_SEC
            )
        }
        log.info("Classified {} as {} / {} (SIC {})", asset.code, sector, industry.ifBlank { "N/A" }, sic)
        return EnrichmentResult.ENRICHED
    }

    /**
     * A registrant without XBRL facts (404) is not a failure of the classification just
     * persisted - log and move on. Throttles and other errors propagate to the caller's mapping.
     */
    private fun snapshotFundamentals(
        asset: Asset,
        cik: String
    ) {
        val facts =
            try {
                secProxy.getCompanyFacts(cik)
            } catch (e: HttpClientErrorException.NotFound) {
                log.warn("No SEC companyfacts for {}:{} (CIK {}) - {}", asset.market.code, asset.code, cik, e.message)
                return
            }
        val snapshot = fundamentalsParser.parse(facts)
        if (snapshot == null) {
            log.warn("SEC companyfacts for {}:{} carried no fiscal-year 10-K measures", asset.market.code, asset.code)
            return
        }
        fundamentalsRepository.save(
            AssetFundamentals(
                assetId = asset.id,
                source = AssetFundamentals.SOURCE_SEC,
                fiscalYearEnd = snapshot.fiscalYearEnd,
                fiscalYear = snapshot.fiscalYear,
                epsDiluted = snapshot.epsDiluted,
                revenue = snapshot.revenue,
                netIncome = snapshot.netIncome,
                dividendsPerShare = snapshot.dividendsPerShare,
                sharesOutstanding = snapshot.sharesOutstanding,
                asOf = dateUtils.date
            )
        )
    }

    /**
     * See [AlphaClassificationEnricher.logNoData] - a NO_DATA outcome stamps the asset as checked
     * and parks it for the staleness window, so it must be visible at WARN with `market:code`.
     */
    private fun logNoData(
        asset: Asset,
        reason: String
    ) = log.warn("No classification data from SEC for {}:{} - {}", asset.market.code, asset.code, reason)

    companion object {
        val SEC_MARKETS = setOf("US", "NASDAQ", "NYSE", "AMEX")
        private val THROTTLE_STATUSES = setOf(HttpStatus.TOO_MANY_REQUESTS, HttpStatus.FORBIDDEN)
    }
}