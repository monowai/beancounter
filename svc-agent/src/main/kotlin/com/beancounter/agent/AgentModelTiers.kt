package com.beancounter.agent

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Model IDs per cost/quality tier (DeepSeek by default).
 *
 * `fast`  — chat, lookups, list/rank queries, news triage. Default deepseek-flash.
 * `smart` — analytical multi-step domains (retirement projections, rebalance
 *           plans, deep asset review). Default deepseek-flash.
 * `deep`  — reserved for queries the LLM itself escalates as ambiguous /
 *           multi-portfolio aggregation. Default deepseek-v4-pro. Currently unused by
 *           the page-context selector — wire in when a domain genuinely needs it.
 * `vision` — reading a brokerage screenshot. Default deepseek-flash. Its own
 *           tier because a gateway may route images apart from chat: waharoa's
 *           deepseek-flash route drops them, deepseek-flash-vision keeps them.
 *
 * Override per environment via `AI_MODEL_FAST` / `AI_MODEL_SMART` / `AI_MODEL_DEEP` / `AI_MODEL_VISION`
 * (mapped in application.yml); the `anthropic` profile uses `ANTHROPIC_MODEL_*`.
 */
@ConfigurationProperties("agent.models")
data class AgentModelTiers(
    var fast: String = DEEPSEEK_FLASH,
    var smart: String = DEEPSEEK_FLASH,
    var deep: String = "deepseek-v4-pro",
    var vision: String = DEEPSEEK_FLASH
)

private const val DEEPSEEK_FLASH = "deepseek-flash"