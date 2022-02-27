package com.beancounter.shell

import com.beancounter.auth.AuthConfig
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.shell.cli.IngestionCommand
import com.beancounter.shell.config.IngestionConfig
import com.beancounter.shell.ingest.AbstractIngester
import com.beancounter.shell.ingest.IngestionFactory
import com.beancounter.shell.ingest.IngestionRequest
import com.beancounter.shell.ingest.TrnWriter
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Service

/**
 * Integration tests for Ingestion.
 */

@SpringBootTest(classes = [IngestionConfig::class, IngestionCommand::class])
@Import(IngestionConfig::class)
class TestIngestCommand {
    @Autowired
    private lateinit var ingestionCommand: IngestionCommand

    @Autowired
    private lateinit var ingestionFactory: IngestionFactory

    @MockBean
    private lateinit var authConfig: AuthConfig

    @MockBean
    private lateinit var portfolioService: PortfolioServiceClient

    private var trnWriter: TrnWriter = Mockito.mock(TrnWriter::class.java)
    private val mockIngester = MockIngester()

    private val pfCode = "ABC"

    private val mock = "mock"

    @BeforeEach
    fun mockServices() {
        Mockito.`when`(portfolioService.getPortfolioByCode(pfCode))
            .thenReturn(getPortfolio(pfCode))
        mockIngester.setPortfolioService(portfolioService)
        Mockito.`when`(trnWriter.id()).thenReturn(mock)
        mockIngester.setTrnWriters(trnWriter)
    }

    @Test
    fun is_IngestionCommandRunning() {
        ingestionFactory.add(mock.uppercase(), mockIngester)
        // Make sure we are not case sensitive when finding the ingestion approach to use.
        Assertions.assertThat(
            ingestionCommand.ingest(
                mock,
                mock,
                pfCode,
                pfCode,
            )
        )
            .isEqualTo("Done")
    }

    @Service
    internal class MockIngester : AbstractIngester() {
        override fun prepare(ingestionRequest: IngestionRequest, trnWriter: TrnWriter) {
            // NoOp
        }

        override val values: List<List<String>>
            get() = ArrayList()
    }
}
