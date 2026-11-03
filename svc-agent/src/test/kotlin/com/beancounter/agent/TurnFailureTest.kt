package com.beancounter.agent

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

private const val OPAQUE_ERROR = "agent-error"

/**
 * DeepSeek's out-of-credit failure on the blocking path. Spring AI's
 * `RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER` formats every non-2xx as
 * `"<status> - <body>"`, so the HTTP status only ever reaches us as the leading
 * token of the message.
 */
internal fun outOfCreditSync(): Throwable =
    RuntimeException(
        "402 - {\"error\":{\"message\":\"Insufficient Balance\",\"type\":\"unknown_error\"," +
            "\"param\":null,\"code\":\"invalid_request_error\"}}"
    )

/**
 * The same provider failure on the streaming path, which goes through WebClient
 * and therefore surfaces a typed [org.springframework.web.reactive.function.client.WebClientResponseException]
 * with a differently shaped message. Both shapes must classify identically.
 */
private fun outOfCreditStream(): Throwable =
    org.springframework.web.reactive.function.client.WebClientResponseException
        .create(402, "Payment Required", org.springframework.http.HttpHeaders(), ByteArray(0), null)

/** The stable failure codes both transports answer with. */
class TurnFailureTest {
    @Test
    fun `classify returns provider-quota for Anthropic credit balance errors`() {
        val anthropicQuota =
            RuntimeException(
                "Response exception, Status: [400 BAD_REQUEST], " +
                    "Body:[{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\"," +
                    "\"message\":\"Your credit balance is too low to access the Anthropic API.\"}}]"
            )
        assertThat(TurnFailure.classify(anthropicQuota))
            .isEqualTo("provider-quota")
    }

    @Test
    fun `classify returns provider-quota for a DeepSeek 402 on either transport`() {
        // DeepSeek answers an empty balance with HTTP 402 / "Insufficient
        // Balance" — nothing like Anthropic's 400 + "credit balance" text, so
        // the original body-string match classified it as a generic
        // agent-error and the UI told the user their request had failed.
        assertThat(TurnFailure.classify(outOfCreditSync()))
            .isEqualTo("provider-quota")
        assertThat(TurnFailure.classify(outOfCreditStream()))
            .isEqualTo("provider-quota")
    }

    @Test
    fun `classify finds the provider failure through a wrapper exception`() {
        // Reactor and Spring AI both re-wrap the provider error before it
        // reaches onErrorResume, so classification has to read the cause chain.
        val wrapped = IllegalStateException("stream failed", outOfCreditStream())

        assertThat(TurnFailure.classify(wrapped)).isEqualTo("provider-quota")
    }

    @Test
    fun `classify does not read a 402 out of an unrelated number`() {
        assertThat(TurnFailure.classify(RuntimeException("tool returned 11402 rows")))
            .isEqualTo(OPAQUE_ERROR)
    }

    @Test
    fun `classify needs one exception to carry both the 400 and the billing text`() {
        // Scanning the joined cause chain would combine a "billing" mentioned
        // in a tool result with a 400 raised somewhere else and call it a
        // quota failure.
        val split =
            RuntimeException(
                "tool result: monthly billing summary",
                RuntimeException("400 - {\"error\":{\"message\":\"model does not exist\"}}")
            )
        assertThat(TurnFailure.classify(split)).isEqualTo(OPAQUE_ERROR)

        // The genuine Anthropic shape — one exception, both signals — still
        // classifies.
        val anthropicBilling =
            RuntimeException("400 - {\"error\":{\"message\":\"Please check your Plans & Billing page.\"}}")
        assertThat(TurnFailure.classify(anthropicBilling)).isEqualTo("provider-quota")
    }

    @Test
    fun `classify returns provider-rate for HTTP 429 and rate-limit text`() {
        assertThat(TurnFailure.classify(RuntimeException("HTTP 429 Too Many Requests")))
            .isEqualTo("provider-rate")
        assertThat(TurnFailure.classify(RuntimeException("anthropic.rate_limit_exceeded")))
            .isEqualTo("provider-rate")
    }

    @Test
    fun `classify returns provider-timeout for TimeoutException and timeout text`() {
        assertThat(TurnFailure.classify(java.util.concurrent.TimeoutException()))
            .isEqualTo("provider-timeout")
        assertThat(TurnFailure.classify(RuntimeException("Read timed out")))
            .isEqualTo("provider-timeout")
    }

    @Test
    fun `classify falls back to agent-error for unknown failures`() {
        assertThat(TurnFailure.classify(RuntimeException("something exploded")))
            .isEqualTo(OPAQUE_ERROR)
        assertThat(TurnFailure.classify(RuntimeException(null as String?)))
            .isEqualTo(OPAQUE_ERROR)
    }
}