package com.beancounter.agent.screenshot

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.web.client.RestClient
import java.nio.file.Files
import java.nio.file.Path

/**
 * Sends each fixture screenshot to the real vision model. It costs tokens, so it
 * never runs in a build: a person runs it on purpose, after a change to the
 * instruction or the model.
 *
 * ```
 * SCREENSHOT_LIVE=true ./gradlew :svc-agent:test --tests '*LiveScreenshotEvaluation'
 * ```
 *
 * `SCREENSHOT_RECORD=true` also rewrites `<fixture>.reply.json`, the reply
 * [ScreenshotReaderTest] replays, for each fixture the model read correctly. The
 * model misreads a digit now and then, so a fixture can fail one run and pass the next. Reads the key from `DEEPSEEK_API_KEY`.
 */
@EnabledIfEnvironmentVariable(named = "SCREENSHOT_LIVE", matches = "true")
class LiveScreenshotEvaluation {
    private val vision =
        DeepSeekVision(
            RestClient.builder().baseUrl(System.getenv("DEEPSEEK_BASE_URL") ?: "https://api.deepseek.com").build(),
            apiKey = System.getenv("DEEPSEEK_API_KEY").orEmpty(),
            model = System.getenv("AI_MODEL_FAST") ?: "deepseek-flash"
        )

    @ParameterizedTest
    @MethodSource("fixtures")
    fun `should read a fixture screenshot with the real model`(fixture: String) {
        var reply = ""
        val reader =
            ScreenshotReader { instruction, screenshot ->
                vision.read(instruction, screenshot).also { reply = it }
            }

        assertMatches(reader.read(Screenshot(fixtureBytes("$fixture.png"), "image/png")), expected(fixture))

        if (System.getenv("SCREENSHOT_RECORD") == "true") {
            Files.writeString(Path.of("src/test/resources/extract/$fixture.reply.json"), reply)
        }
    }

    companion object {
        @JvmStatic
        fun fixtures() = FIXTURES
    }
}