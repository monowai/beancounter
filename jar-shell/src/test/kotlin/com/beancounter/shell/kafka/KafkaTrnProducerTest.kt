package com.beancounter.shell.kafka

import com.beancounter.client.ingest.TrnAdapter
import com.beancounter.client.sharesight.ShareSightFactory
import com.beancounter.common.input.ImportFormat
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.Asset
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.shell.Constants.Companion.MOCK
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.cloud.stream.function.StreamBridge

/**
 * Unit test for KafkaTrnProducer.
 * Tests the write() method logic using mocked dependencies.
 */
class KafkaTrnProducerTest {
    @Test
    fun `write should send message via StreamBridge when asset is resolved`() {
        // Given
        val shareSightFactory = mock(ShareSightFactory::class.java)
        val streamBridge = mock(StreamBridge::class.java)
        val trnAdapter = mock(TrnAdapter::class.java)

        val producer = KafkaTrnProducer(shareSightFactory, streamBridge)

        val row: List<String> = listOf("ABC", "ABC", "ABC")
        val asset: Asset = getTestAsset(MOCK, "ABC")

        val trnRequest =
            TrustedTrnImportRequest(
                getPortfolio(),
                importFormat = ImportFormat.SHARESIGHT,
                row = row
            )

        `when`(shareSightFactory.adapter(row)).thenReturn(trnAdapter)
        `when`(trnAdapter.resolveAsset(row)).thenReturn(asset)
        `when`(streamBridge.send("csvImport-out-0", trnRequest)).thenReturn(true)

        // When
        producer.write(trnRequest)

        // Then
        verify(streamBridge).send("csvImport-out-0", trnRequest)
    }

    @Test
    fun `write should not send message when asset cannot be resolved`() {
        // Given
        val shareSightFactory = mock(ShareSightFactory::class.java)
        val streamBridge = mock(StreamBridge::class.java)
        val trnAdapter = mock(TrnAdapter::class.java)

        val producer = KafkaTrnProducer(shareSightFactory, streamBridge)

        val row: List<String> = listOf("ABC", "ABC", "ABC")

        val trnRequest =
            TrustedTrnImportRequest(
                getPortfolio(),
                importFormat = ImportFormat.SHARESIGHT,
                row = row
            )

        `when`(shareSightFactory.adapter(row)).thenReturn(trnAdapter)
        `when`(trnAdapter.resolveAsset(row)).thenReturn(null)

        // When
        producer.write(trnRequest)

        // Then
        verify(streamBridge, never()).send("csvImport-out-0", trnRequest)
    }
}