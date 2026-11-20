package com.beancounter.marketdata.payslip

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.boot.origin.OriginTrackedValue
import org.springframework.core.env.PropertySource
import org.springframework.core.io.ClassPathResource

/**
 * The payslip bindings must name the destinations and group agreed with svc-retire; a typo here
 * leaves both sides running with nothing arriving.
 */
class PayslipBindingConfigTest {
    @Test
    fun `should bind the payslip due producer and the payslip trn consumer to the agreed destinations`() {
        val config = yaml("application.yml")

        assertThat(config["spring.cloud.function.definition"] as String).contains("payslipTrnConsumer")
        assertThat(config["spring.cloud.stream.bindings.payslipDue-out-0.destination"])
            .isEqualTo("bc-payslip-due-dev")
        assertThat(config["spring.cloud.stream.bindings.payslipTrnConsumer-in-0.destination"])
            .isEqualTo("bc-payslip-trn-dev")
        assertThat(config["spring.cloud.stream.bindings.payslipTrnConsumer-in-0.group"]).isEqualTo("bc-data")
    }

    @Test
    fun `should bind the test destinations in the test profile`() {
        val config = yaml("application-test.yaml")

        assertThat(config["spring.cloud.stream.bindings.payslipDue-out-0.destination"])
            .isEqualTo("test-payslip-due")
        assertThat(config["spring.cloud.stream.bindings.payslipTrnConsumer-in-0.destination"])
            .isEqualTo("test-payslip-trn")
    }

    private fun yaml(name: String): Map<String, Any?> =
        YamlPropertySourceLoader()
            .load(name, ClassPathResource(name))
            .flatMap { source: PropertySource<*> ->
                @Suppress("UNCHECKED_CAST")
                (source.source as Map<String, Any?>).entries
            }.associate { it.key to ((it.value as? OriginTrackedValue)?.value ?: it.value) }
}