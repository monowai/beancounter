package com.beancounter.agent

import java.net.ConnectException
import java.nio.channels.UnresolvedAddressException

/**
 * Why a turn failed, as the stable client-facing code both transports share,
 * with the HTTP status and fallback prose `/query` answers it with.
 * bc-view keys its copy off these codes (see lib/utils/agent/agentErrors.ts);
 * renaming one is a contract change.
 */
object TurnFailure {
    const val NO_LLM = "no-llm"

    /** The caller has used this week's AI turns ([com.beancounter.agent.quota.AgentQuota]). */
    const val AI_QUOTA = "ai-quota"

    /** An administrator has turned AI off for the caller (a weekly limit of 0). */
    const val AI_DISABLED = "ai-disabled"

    /** The caller has not confirmed their email address, so is served cached Asset Reviews only. */
    const val EMAIL_UNVERIFIED = "email-unverified"

    const val PROVIDER_QUOTA = "provider-quota"
    const val PROVIDER_RATE = "provider-rate"
    const val PROVIDER_TIMEOUT = "provider-timeout"
    const val AGENT_ERROR = "agent-error"

    /**
     * A Holding Analysis of an asset the portfolio does not hold, or of a
     * portfolio the caller cannot see — one code, so neither is revealed.
     */
    const val NOT_HELD = "not-held"

    /**
     * The turn ended on a token cap with nothing to show for it — on the
     * Independence domain this is the context window filling with tool
     * results (a full year-by-year projection runs to tens of thousands of
     * tokens), leaving no output budget for the answer itself.
     */
    const val ANSWER_TRUNCATED = "answer-truncated"

    /** The turn ended normally but produced no answer text. */
    const val EMPTY_ANSWER = "empty-answer"

    // Finish reasons that mean "stopped because it ran out of room", as
    // opposed to "finished what it had to say". DeepSeek/OpenAI say
    // "length"; Anthropic says "max_tokens".
    val TRUNCATION_FINISH_REASONS = setOf("length", "max_tokens")

    private const val BAD_REQUEST = 400
    private const val FORBIDDEN = 403
    private const val NOT_FOUND = 404
    private const val PAYMENT_REQUIRED = 402
    private const val TOO_MANY_REQUESTS = 429
    private const val INTERNAL_ERROR = 500
    private const val UNPROCESSABLE_CONTENT = 422
    private const val GATEWAY_TIMEOUT = 504

    // Only a status at the very start of the message is trusted — that is
    // where Spring AI's "<status> - <body>" puts it. Digit boundaries stop
    // a number inside a tool result from reading as a status.
    private val LEADING_HTTP_STATUS = Regex("""^\s*(\d{3})(?!\d)""")
    private const val CAUSE_CHAIN_DEPTH = 5

    /**
     * Map an upstream exception into a stable, opaque error code, used by both
     * transports. The client renders a friendly message keyed off the code;
     * the raw exception text never reaches the client.
     *
     *   `provider-quota`   — The LLM account has no credit left. Anthropic
     *                        says so with HTTP 400 + "credit balance";
     *                        DeepSeek with HTTP 402 + "Insufficient Balance".
     *                        Nothing the caller does can clear it — an admin
     *                        has to top the account up.
     *   `provider-rate`    — Provider rate-limited the request (HTTP 429).
     *   `provider-timeout` — Upstream took too long.
     *   `agent-error`      — Anything else.
     *
     * An [UnansweredTurn] carries its own code. Both the exception and its
     * causes are inspected: Reactor and Spring AI re-wrap the provider error
     * on the way out, so the status is often one or two levels down.
     */
    fun classify(e: Throwable): String {
        if (e is UnansweredTurn) return e.code
        val chain = causeChain(e)
        val message = chain.joinToString(" | ") { it.message.orEmpty() }
        val statuses = chain.mapNotNull(::httpStatusOf)
        return when {
            isOutOfCredit(chain, message, statuses) -> PROVIDER_QUOTA
            isRateLimited(message, statuses) -> PROVIDER_RATE
            isTimedOut(chain, message) -> PROVIDER_TIMEOUT
            else -> AGENT_ERROR
        }
    }

    /**
     * Why a turn ended with no answer text. `length`/`max_tokens` mean the
     * model ran out of room — on the heavy domains that is the context window
     * being consumed by tool results, which is actionable (ask something
     * narrower) in a way that a bare "something went wrong" is not.
     */
    fun emptyAnswerCode(finishReason: String?): String =
        if (finishReason?.lowercase() in TRUNCATION_FINISH_REASONS) ANSWER_TRUNCATED else EMPTY_ANSWER

    /**
     * True when the failure is a transient network/connectivity error worth
     * retrying — a connection or DNS-resolution failure anywhere in the cause
     * chain. Deterministic provider errors (4xx, rate limits) are excluded so
     * we don't retry the inevitable.
     */
    fun isTransientConnectivity(error: Throwable): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is ConnectException || cause is UnresolvedAddressException) return true
            cause = cause.cause
        }
        return false
    }

    /**
     * HTTP status for a failed blocking `/query`, so the browser can tell an
     * administrative outage from a broken request without parsing prose.
     * 402 is passed straight through: it means the same thing to us as it does
     * to the provider — the account behind the feature needs topping up.
     */
    fun statusFor(errorCode: String): Int =
        when (errorCode) {
            PROVIDER_QUOTA -> PAYMENT_REQUIRED
            PROVIDER_RATE, AI_QUOTA -> TOO_MANY_REQUESTS
            PROVIDER_TIMEOUT -> GATEWAY_TIMEOUT
            // Not a server fault: the request was well-formed, the provider
            // call succeeded, and the model finished — it just produced no
            // answer, usually because tool results filled its context window.
            // 500 would read as "bc-agent broke" and page on a condition only
            // a narrower question can fix. The streaming path reports the same
            // codes over a 200 SSE stream; the code, not the status, is the
            // contract both transports share.
            ANSWER_TRUNCATED, EMPTY_ANSWER -> UNPROCESSABLE_CONTENT
            NOT_HELD -> NOT_FOUND
            EMAIL_UNVERIFIED, AI_DISABLED -> FORBIDDEN
            else -> INTERNAL_ERROR
        }

    /**
     * Fallback prose for clients that render the body instead of keying off
     * [AgentResponse.error]. Deliberately says whose problem it is; the rich
     * copy lives in the UI, next to the retry affordances.
     */
    fun messageFor(errorCode: String): String =
        when (errorCode) {
            PROVIDER_QUOTA -> {
                "AI features are paused: the AI provider account has run out of credit. " +
                    "This is a service administration issue, not a problem with your request. " +
                    "Please try again once the balance has been topped up."
            }
            PROVIDER_RATE -> {
                "The AI provider is rate-limiting requests. Please wait a moment and try again."
            }
            PROVIDER_TIMEOUT -> {
                "The AI provider took too long to respond. Please try again."
            }
            ANSWER_TRUNCATED -> {
                "The question needed more data than the AI can hold at once, so it ran out of room " +
                    "before answering. Try asking about one plan, phase or holding at a time."
            }
            EMPTY_ANSWER -> {
                "The AI finished without producing an answer. Please try again, or rephrase the question."
            }
            NOT_HELD -> {
                "No holding of this asset found in that portfolio."
            }
            AI_QUOTA -> {
                "You have used this week's AI questions. Each one frees up seven days after it was asked."
            }
            AI_DISABLED -> {
                "AI is turned off for your account. Ask the administrator to turn it back on."
            }
            EMAIL_UNVERIFIED -> {
                "Confirm your email address to ask the AI. Cached Asset Reviews are still available."
            }
            else -> {
                "The agent failed to process the request."
            }
        }

    private fun isOutOfCredit(
        chain: List<Throwable>,
        message: String,
        statuses: List<Int>
    ): Boolean {
        // Distinctive phrases are matched across the whole chain — no other
        // failure says "credit balance" or "insufficient balance".
        val isCreditBalance = message.contains("credit balance", ignoreCase = true)
        val isOutOfCredit =
            message.contains("insufficient balance", ignoreCase = true) ||
                message.contains("insufficient_quota", ignoreCase = true)
        // "billing" is not distinctive enough for that, and neither is a bare
        // "400". Anthropic's billing rejection is only trusted when one
        // exception carries both — otherwise a "billing" in a tool result and
        // a 400 from an unrelated wrapper would combine into a false quota.
        val isBilling400 =
            chain.any { cause ->
                httpStatusOf(cause) == BAD_REQUEST &&
                    cause.message.orEmpty().contains("billing", ignoreCase = true)
            }
        return statuses.contains(PAYMENT_REQUIRED) || isCreditBalance || isBilling400 || isOutOfCredit
    }

    // Likewise a bare "429": the status is read from where it is trusted,
    // and the wording covers providers that report it in prose.
    private fun isRateLimited(
        message: String,
        statuses: List<Int>
    ): Boolean =
        statuses.contains(TOO_MANY_REQUESTS) ||
            message.contains("too many requests", ignoreCase = true) ||
            message.contains("rate limit", ignoreCase = true) ||
            message.contains("rate_limit", ignoreCase = true)

    private fun isTimedOut(
        chain: List<Throwable>,
        message: String
    ): Boolean =
        chain.any { it is java.util.concurrent.TimeoutException } ||
            message.contains("timed out", ignoreCase = true) ||
            message.contains("timeout", ignoreCase = true)

    /** The exception plus its causes, depth-capped so a cyclic chain can't hang us. */
    private fun causeChain(e: Throwable): List<Throwable> =
        generateSequence(e) { current -> current.cause?.takeIf { it !== current } }
            .take(CAUSE_CHAIN_DEPTH)
            .toList()

    /**
     * The provider's HTTP status, where it is actually knowable. WebClient
     * (streaming path) carries it as a typed field; Spring AI's blocking error
     * handler only formats it as `"<status> - <body>"`, so it is read back from
     * the head of the message. Anything else yields null rather than a guess —
     * a status scraped from anywhere in the text would match numbers that are
     * part of a tool result.
     */
    private fun httpStatusOf(e: Throwable): Int? =
        when (e) {
            is org.springframework.web.reactive.function.client.WebClientResponseException -> {
                e.statusCode.value()
            }
            else -> {
                LEADING_HTTP_STATUS
                    .find(e.message.orEmpty())
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
            }
        }
}

/** A turn that could not be answered, carrying the stable code it fails with. */
class UnansweredTurn(
    val code: String
) : RuntimeException(code)