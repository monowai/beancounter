package com.beancounter.marketdata.classification

import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetClassification
import com.beancounter.common.model.ClassificationItem
import com.beancounter.common.model.ClassificationLevel
import com.beancounter.common.model.ClassificationStandard
import com.beancounter.common.model.Market
import com.beancounter.common.model.Status
import com.beancounter.marketdata.providers.eodhd.EodhdConfig
import com.beancounter.marketdata.providers.eodhd.EodhdProxy
import com.beancounter.marketdata.providers.eodhd.model.EodhdEtfData
import com.beancounter.marketdata.providers.eodhd.model.EodhdFundamentals
import com.beancounter.marketdata.providers.eodhd.model.EodhdGeneral
import com.beancounter.marketdata.providers.eodhd.model.EodhdSectorWeight
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

/**
 * Unit tests for [EodhdClassificationEnricher].
 * Verifies observable persistence effects: parsed weights, sector/industry classification,
 * and re-enrichment clearing — not just mock surface.
 */
class EodhdClassificationEnricherTest {
    private lateinit var eodhdConfig: EodhdConfig
    private lateinit var eodhdProxy: EodhdProxy
    private lateinit var classificationService: ClassificationService
    private lateinit var enricher: EodhdClassificationEnricher

    private val market = Market(code = "NASDAQ", name = "NASDAQ")
    private val standard =
        ClassificationStandard(
            id = "std-eodhd",
            key = ClassificationStandard.EODHD,
            name = "EODHD Sector Classification",
            provider = ClassificationStandard.PROVIDER_EODHD
        )

    private fun item(
        code: String,
        level: ClassificationLevel
    ) = ClassificationItem(id = "item-$code", standard = standard, level = level, code = code, name = code)

    @BeforeEach
    fun setUp() {
        eodhdConfig = mock()
        eodhdProxy = mock()
        classificationService = mock()
        enricher = EodhdClassificationEnricher(eodhdConfig, eodhdProxy, classificationService)

        whenever(eodhdConfig.apiKey).thenReturn("demo")
        whenever(classificationService.getEodhdStandard()).thenReturn(standard)
        // getOrCreateItem takes nullable name/parent; anyOrNull() matches the null positions
        // (mockito-kotlin any() delegates to Mockito.any(Class) which rejects null).
        whenever(
            classificationService.getOrCreateItem(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        ).thenAnswer { item("X", it.getArgument(1)) }
    }

    private fun etf() =
        Asset(
            id = "etf-1",
            code = "VTI",
            name = "Vanguard Total",
            category = "ETF",
            market = market,
            status = Status.Active
        )

    private fun equity() =
        Asset(id = "eq-1", code = "AAPL", name = "Apple", category = "EQUITY", market = market, status = Status.Active)

    @Test
    fun `enrichEtf adds sector exposures from Sector_Weights`() {
        whenever(eodhdConfig.getPriceCode(any())).thenReturn("VTI.US")
        whenever(eodhdProxy.getFundamentals(eq("VTI.US"), any())).thenReturn(
            EodhdFundamentals(
                etfData =
                    EodhdEtfData(
                        sectorWeights =
                            mapOf(
                                "Technology" to EodhdSectorWeight(equityPct = "33.52294"),
                                "Financial Services" to EodhdSectorWeight(equityPct = "11.97072"),
                                // zero-weight sector is skipped
                                "Utilities" to EodhdSectorWeight(equityPct = "0")
                            )
                    )
            )
        )

        val result = enricher.enrichClassification(etf())

        assertThat(result).isTrue()
        verify(classificationService).clearExposures("etf-1")

        val weights = argumentCaptor<BigDecimal>()
        verify(classificationService, times(2)).addExposure(any(), any(), any(), weights.capture(), any())
        assertThat(weights.allValues).containsExactlyInAnyOrder(
            BigDecimal("33.52294"),
            BigDecimal("11.97072")
        )
    }

    @Test
    fun `enrichEquity classifies sector and industry from General`() {
        whenever(eodhdConfig.getPriceCode(any())).thenReturn("AAPL.US")
        whenever(eodhdProxy.getFundamentals(eq("AAPL.US"), any())).thenReturn(
            EodhdFundamentals(
                general = EodhdGeneral(type = "Common Stock", sector = "Technology", industry = "Consumer Electronics")
            )
        )

        val result = enricher.enrichClassification(equity())

        assertThat(result).isTrue()
        val levels = argumentCaptor<ClassificationLevel>()
        verify(classificationService, times(2)).classifyAsset(
            any(),
            any(),
            any(),
            levels.capture(),
            eq(AssetClassification.SOURCE_EODHD)
        )
        assertThat(levels.allValues).containsExactly(ClassificationLevel.SECTOR, ClassificationLevel.INDUSTRY)
    }

    @Test
    fun `returns false when no fundamentals data`() {
        whenever(eodhdConfig.getPriceCode(any())).thenReturn("VTI.US")
        whenever(eodhdProxy.getFundamentals(any(), any())).thenReturn(EodhdFundamentals())

        val result = enricher.enrichClassification(etf())

        assertThat(result).isFalse()
        verify(classificationService, never()).addExposure(any(), any(), any(), any(), any())
    }
}