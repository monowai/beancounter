package com.beancounter.position.marketvalue

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.client.services.ClassificationClient
import com.beancounter.client.services.PriceService
import com.beancounter.common.contracts.BulkClassificationResponse
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Positions
import com.beancounter.position.Constants.Companion.US
import com.beancounter.position.utils.TestHelpers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException

class ValuationDataSourceTest {
    private val priceService: PriceService = mock()
    private val fxService: FxService = mock()
    private val fxUtils: FxUtils = mock()
    private val classificationClient: ClassificationClient = mock()
    private val dataSource =
        ValuationDataSource(priceService, fxService, fxUtils, classificationClient, mock<TokenService>())
    private val portfolio = TestHelpers.createTestPortfolio("ValuationDataSourceTest")
    private val positions: Positions =
        TestHelpers.createTestPositions(
            portfolio,
            listOf(TestHelpers.createTestPosition(TestHelpers.createTestAsset("Asset1", US.code), portfolio))
        )

    init {
        whenever(fxUtils.buildRequest(any(), any())).thenReturn(FxRequest())
        whenever(priceService.getPrices(any(), any())).thenReturn(PriceResponse())
        whenever(classificationClient.getClassifications(any())).thenReturn(BulkClassificationResponse())
    }

    @Test
    fun `a cancelled read stays a cancellation`() {
        val cancelled = CancellationException("caller went away")
        whenever(fxService.getRates(any(), any())).thenThrow(cancelled)

        val thrown = catchThrowable { runBlocking { dataSource.read(positions, "token") } }

        assertThat(thrown).isInstanceOf(CancellationException::class.java)
    }

    @Test
    fun `a failed read propagates and logs which read and portfolio failed`() {
        val failure = BusinessException("fx unavailable")
        whenever(fxService.getRates(any(), any())).thenThrow(failure)
        val logger = LoggerFactory.getLogger(ValuationDataSource::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        val thrown =
            try {
                catchThrowable { runBlocking { dataSource.read(positions, "token") } }
            } finally {
                logger.detachAppender(appender)
            }

        // Coroutine stack-trace recovery may hand back a copy, so match type and message.
        assertThat(thrown).isInstanceOf(BusinessException::class.java).hasMessage(failure.message)
        assertThat(appender.list).anySatisfy {
            assertThat(it.level).isEqualTo(Level.ERROR)
            assertThat(it.formattedMessage).contains("FX").contains(portfolio.code)
        }
    }
}