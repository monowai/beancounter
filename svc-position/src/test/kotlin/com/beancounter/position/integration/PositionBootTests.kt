package com.beancounter.position.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.context.WebApplicationContext

@SpringBootTest
@Tag("slow")
@ActiveProfiles("test")
internal class PositionBootTests @Autowired private constructor(private val context: WebApplicationContext) {
    @Test
    fun contextLoads() {
        assertThat(context).isNotNull
    }
}
