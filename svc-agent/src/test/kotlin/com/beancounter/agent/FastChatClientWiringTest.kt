package com.beancounter.agent

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles

/**
 * Boot-safety guard for the non-thinking "fast" DeepSeek client. The fast model
 * is hand-built (custom DeepSeekApi with the thinking-disabled RestClient/WebClient).
 * This proves that wiring constructs cleanly alongside the autoconfigured thinking
 * client — i.e. both `chatClient` and `fastChatClient` beans exist under the
 * `deepseek` profile. Makes no LLM call.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "spring.ai.deepseek.api-key=dummy-key-for-construction",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://beancounter.eu.auth0.com/",
        "auth.audience=https://holdsworth.app"
    ]
)
@ActiveProfiles("deepseek")
class FastChatClientWiringTest {
    @Autowired
    private lateinit var context: ApplicationContext

    @Test
    fun `both thinking and non-thinking DeepSeek clients are wired`() {
        assertThat(context.containsBean("chatClient")).isTrue()
        assertThat(context.containsBean("fastChatClient")).isTrue()
    }
}