package com.beancounter.shell

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
import java.util.ArrayList

@SpringBootTest(classes = [IngestionConfig::class, IngestionCommand::class])
@Import(IngestionConfig::class)
class TestIngestCommand {
    @Autowired
    private lateinit var ingestionCommand: IngestionCommand

    @Autowired
    private lateinit var ingestionFactory: IngestionFactory

    @MockBean
    private lateinit var portfolioService: PortfolioServiceClient

    @MockBean
    private lateinit var trnWriter: TrnWriter
    private val mockIngester = MockIngester()

    @BeforeEach
    fun mockServices() {
        Mockito.`when`(portfolioService.getPortfolioByCode("ABC"))
            .thenReturn(getPortfolio("ABC"))
        mockIngester.setPortfolioService(portfolioService)
        Mockito.`when`(trnWriter.id()).thenReturn("mock")
        mockIngester.setTrnWriters(trnWriter)
    }

    @Test
    fun is_IngestionCommandRunning() {
        ingestionFactory.add("MOCK", mockIngester)
        // Make sure we are not case sensitive when finding the ingestion approach to use.
        Assertions.assertThat(
            ingestionCommand.ingest(
                "mock",
                trnWriter.id(),
                "ABC",
                "ABC",
                null
            )
        )
            .isEqualTo("Done")
    }

    @Service
    internal class MockIngester : AbstractIngester() {
        override fun prepare(ingestionRequest: IngestionRequest, trnWriter: TrnWriter) {
            // Noop
        }

        override fun getValues(): List<List<String>> {
            return ArrayList()
        }
    }
}
