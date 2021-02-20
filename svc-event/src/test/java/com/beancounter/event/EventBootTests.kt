package com.beancounter.event

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.context.WebApplicationContext

@SpringBootTest(classes = [EventBoot::class])
@Tag("slow")
@ActiveProfiles("test")
internal class EventBootTests @Autowired private constructor(private val context: WebApplicationContext) {
    @Test
    fun contextLoads() {
        Assertions.assertThat(context).isNotNull
    }
}
