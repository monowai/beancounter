package com.beancounter.position.config

import com.beancounter.client.services.ClassificationClient
import com.beancounter.common.contracts.BulkClassificationResponse
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * Test configuration that provides a mock ClassificationClient for integration tests.
 */
@TestConfiguration
class TestClassificationConfig {
    @Bean
    @Primary
    fun classificationClient(): ClassificationClient {
        val mockClient = mock<ClassificationClient>()
        whenever(mockClient.getClassifications(any())).thenReturn(BulkClassificationResponse())
        return mockClient
    }
}