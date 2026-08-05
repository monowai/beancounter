package com.beancounter.marketdata.trn

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.boot.origin.OriginTrackedValue
import org.springframework.core.env.PropertySource
import org.springframework.core.io.ClassPathResource

/**
 * A consumer that throws exhausts its retry policy and the message is acked away — the
 * payload is gone and the user who triggered it sees a success dialog (#1067). Nothing
 * about the trn consumers is idempotent enough to retry blindly later, so the payload has
 * to survive somewhere a human can look: the binder's dead-letter queue.
 *
 * Asserting on the binding config is asserting the behaviour — the binder does the rest,
 * and there is no way to observe a DLQ without a live broker.
 */
class ImportDlqConfigTest {
    private val consumers =
        listOf(
            "csvImportConsumer-in-0",
            "trnEventConsumer-in-0"
        )

    @Test
    fun `trn consumers dead-letter a failed message instead of discarding it`() {
        val config = applicationYaml()

        consumers.forEach { binding ->
            assertThat(config["spring.cloud.stream.rabbit.bindings.$binding.consumer.auto-bind-dlq"])
                .describedAs("$binding must declare a DLQ")
                .isEqualTo(true)
            assertThat(config["spring.cloud.stream.rabbit.bindings.$binding.consumer.republish-to-dlq"])
                .describedAs("$binding must republish the failed payload, keeping the failure headers")
                .isEqualTo(true)
        }
    }

    private fun applicationYaml(): Map<String, Any?> =
        YamlPropertySourceLoader()
            .load(
                "application",
                ClassPathResource("application.yml")
            ).flatMap { source: PropertySource<*> ->
                @Suppress("UNCHECKED_CAST")
                (source.source as Map<String, Any?>).entries
            }.associate { it.key to unwrap(it.value) }

    /** The yaml loader wraps every value with its source origin. */
    private fun unwrap(value: Any?): Any? = (value as? OriginTrackedValue)?.value ?: value
}