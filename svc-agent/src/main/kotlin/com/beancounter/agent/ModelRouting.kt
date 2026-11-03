package com.beancounter.agent

import org.springframework.ai.anthropic.AnthropicCacheOptions
import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.deepseek.DeepSeekChatOptions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * How a turn reaches a model: which configured client asks it, which model
 * answers, and the per-call options that pick that model on the provider.
 */
data class TurnModel(
    val client: ChatClient,
    /** The model selected for the turn ([ChatModelSelector]). */
    val modelId: String,
    /** Whether the turn reasons ([ModelRouting.thinks]). */
    val thinks: Boolean,
    /** Per-call options, or null where the client's configured model answers. */
    val options: ChatOptions.Builder<*>?,
    /**
     * The model reported to telemetry and the client: [modelId] only where the
     * per-call Anthropic override names what actually answers — on other
     * providers the configured ChatClient picks the model, and the selected
     * id would mislead.
     */
    val reportedModelId: String?
)

/** Routes each turn to a configured LLM client and model for the active provider. */
@Component
class ModelRouting(
    @Autowired(required = false) private val chatClient: ChatClient?,
    @Autowired(required = false)
    @Qualifier("fastChatClient")
    private val fastChatClient: ChatClient?,
    @Autowired(required = false) private val anthropicCacheOptions: AnthropicCacheOptions?,
    private val chatModelSelector: ChatModelSelector,
    private val environment: Environment
) {
    private companion object {
        // DeepSeek output caps. In thinking mode `max_tokens` covers
        // reasoning_content plus the answer, so a thinking turn needs the
        // deep-tier headroom; the non-thinking fast client keeps the small cap.
        const val FAST_MAX_TOKENS = 4096
        const val THINKING_MAX_TOKENS = 16384
        const val THINKING_BUDGET_TOKENS = 4096L
    }

    /** True when an LLM is configured; every other member assumes it is. */
    val available: Boolean
        get() = chatClient != null

    // Selected provider: the `agent.provider` property, `deepseek` when absent.
    private val provider: String
        get() = environment.getProperty(AgentProvider.PROPERTY, AgentProvider.DEFAULT)

    // Anthropic-only feature: the per-call model override uses
    // AnthropicChatOptions. For any other provider, skip the override and
    // let the configured ChatClient's default model run.
    private val anthropicActive: Boolean
        get() = provider == AgentProvider.ANTHROPIC

    private val deepseekActive: Boolean
        get() = provider == AgentProvider.DEEPSEEK

    /**
     * The client, model and options for [turn]. Per-call options REPLACE (not
     * merge) the ChatClient's default options — Anthropic cache config is
     * re-applied in [buildOptions], or every request silently loses prompt
     * caching.
     */
    fun modelFor(turn: AgentQuery): TurnModel {
        val modelId = chatModelSelector.selectFor(turn.context, turn.deepThink)
        val thinks = thinks(turn)
        return TurnModel(
            client = clientFor(thinks),
            modelId = modelId,
            thinks = thinks,
            options = buildOptions(modelId, turn.deepThink, thinks),
            reportedModelId = reported(modelId)
        )
    }

    /** [modelId] where it names the model that answers (the Anthropic surface), else null. */
    fun reported(modelId: String): String? = modelId.takeIf { anthropicActive }

    /**
     * The model that actually answers [turn]: the selected tier where the
     * per-call override applies ([buildOptions]: Anthropic and DeepSeek, where
     * `deepThink` selects the deep tier's model), else the provider's own
     * configured model, named by the provider since every tier gets it.
     */
    fun answeringModel(turn: AgentQuery): String =
        if (anthropicActive || deepseekActive) chatModelSelector.selectFor(turn.context, turn.deepThink) else provider

    /**
     * Build per-call ChatOptions for the active LLM surface.
     *
     * Returns `null` for surfaces that don't support a per-call model override
     * (Ollama / OpenAI), in which case the ChatClient's configured default
     * model answers and tier escalation is silently ignored.
     *
     * `deepThink` raises `maxTokens` so the deep tier (deepseek-v4-pro /
     * claude-opus-*) has headroom for chain-of-thought + final answer; on the
     * Anthropic surface it also explicitly enables thinking with a 4k budget
     * (Claude 4 has thinking on by default; setting it explicitly documents
     * intent and lets the budget be tuned).
     *
     * `think` (the interactive Chat FAB) routes to DeepSeek's thinking client,
     * where `max_tokens` caps reasoning_content *and* the answer together —
     * DeepSeek's own default in thinking mode is 64k. Under the fast-tier 4k
     * cap a v4-flash turn on kauri spent the whole budget reasoning about a
     * 27k-token prompt and finished on `length` with no answer at all, so a
     * thinking turn gets the same headroom as the deep tier.
     *
     * The thinking client defaults every request to `reasoning_effort: low`
     * (beancounter#1128); a `think` + `deepThink` turn pins `high` here so the
     * deep tier keeps full effort. Effort is never set without `think` — any
     * value re-enables thinking on the fast client. Callers pass [thinks], so
     * `think` is also true for a SMART page's pre-canned prompt: such a turn
     * reasons at low effort, or at high effort on the deep tier.
     */
    internal fun buildOptions(
        modelId: String,
        deepThink: Boolean,
        think: Boolean = false
    ): ChatOptions.Builder<*>? =
        when {
            anthropicActive -> {
                val b = AnthropicChatOptions.builder().model(modelId)
                anthropicCacheOptions?.let(b::cacheOptions)
                if (deepThink) {
                    b
                        .maxTokens(THINKING_MAX_TOKENS)
                        // Spring AI 2.0: the two-arg thinking(type, budget) is
                        // gone; thinkingEnabled(budgetTokens) is the explicit
                        // enable + budget call (4k budget preserved).
                        .thinkingEnabled(THINKING_BUDGET_TOKENS)
                }
                b
            }
            deepseekActive -> {
                val b =
                    DeepSeekChatOptions
                        .builder()
                        .model(modelId)
                        .maxTokens(if (deepThink || think) THINKING_MAX_TOKENS else FAST_MAX_TOKENS)
                if (deepThink && think) b.reasoningEffortHigh()
                b
            }
            else -> {
                null
            }
        }

    /**
     * Whether this turn thinks: asked for (the Chat FAB), or a SMART page's pre-canned prompt,
     * which reasons at the thinking client's low effort ([ChatModelSelector.reasonsFor]).
     */
    private fun thinks(turn: AgentQuery): Boolean = turn.think || chatModelSelector.reasonsFor(turn.context)

    /**
     * Pick the LLM client for a turn. Pre-canned prompts (the default,
     * `think=false`) use the non-thinking [fastChatClient] for lowest latency,
     * except on the SMART pages, which reason at low effort; the interactive
     * Chat FAB sets `think=true` to keep DeepSeek thinking mode.
     * Falls back to the thinking client if no fast client is configured.
     * Callers check [available] first.
     */
    private fun clientFor(thinks: Boolean): ChatClient {
        val client = if (thinks) chatClient else fastChatClient ?: chatClient
        return checkNotNull(client) { "No LLM is configured" }
    }
}