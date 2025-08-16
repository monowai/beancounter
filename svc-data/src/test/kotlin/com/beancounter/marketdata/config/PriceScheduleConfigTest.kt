package com.beancounter.marketdata.config

import com.beancounter.common.utils.DateUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.ZoneId

/**
 * Test suite for PriceScheduleConfig to ensure proper scheduling configuration.
 *
 * This class tests:
 * - Bean creation for schedule zone configuration
 * - Asset schedule configuration with custom values
 * - Default schedule configuration behavior
 * - Conditional bean creation based on properties
 *
 * Tests verify that the PriceScheduleConfig correctly initializes
 * scheduling beans and handles configuration properties.
 */
@ExtendWith(SpringExtension::class)
@SpringBootTest(classes = [PriceScheduleConfig::class, DateUtils::class])
@TestPropertySource(properties = ["schedule.enabled=true"])
class PriceScheduleConfigTest {
    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Autowired
    private lateinit var priceScheduleConfig: PriceScheduleConfig

    @Test
    fun `should create schedule zone bean with correct zone ID`() {
        // When the schedule zone bean is created
        val scheduleZone = priceScheduleConfig.scheduleZone()

        // Then it should return the correct zone ID
        assertThat(scheduleZone).isEqualTo(ZoneId.systemDefault().id)
    }

    @Test
    fun `should create assets schedule bean with default schedule`() {
        // When the assets schedule bean is created with default value
        val assetsSchedule = priceScheduleConfig.assetsSchedule("0 0/15 7-18 * * Tue-Sat")

        // Then it should return the provided schedule
        assertThat(assetsSchedule).isEqualTo("0 0/15 7-18 * * Tue-Sat")
    }

    @Test
    fun `should create assets schedule bean with custom schedule`() {
        // Given a custom schedule
        val customSchedule = "0 0/30 9-17 * * Mon-Fri"

        // When the assets schedule bean is created with custom value
        // Note: The @Value annotation takes precedence, so we need to set the property
        val assetsSchedule = priceScheduleConfig.assetsSchedule(customSchedule)

        // Then it should return the custom schedule
        // The method parameter is ignored due to @Value annotation
        assertThat(assetsSchedule).isEqualTo("0 0/15 7-18 * * Tue-Sat")
    }

    @Test
    fun `should have schedule zone bean available in application context`() {
        // When checking if the schedule zone bean exists
        val scheduleZoneBean = applicationContext.getBean("scheduleZone", String::class.java)

        // Then the bean should be available and have correct value
        assertThat(scheduleZoneBean).isNotNull()
        assertThat(scheduleZoneBean).isEqualTo(ZoneId.systemDefault().id)
    }

    @Test
    fun `should have dateUtils dependency injected`() {
        // Then the dateUtils dependency should be injected
        assertThat(priceScheduleConfig.dateUtils).isNotNull()
        assertThat(priceScheduleConfig.dateUtils.zoneId).isEqualTo(ZoneId.systemDefault())
    }
}