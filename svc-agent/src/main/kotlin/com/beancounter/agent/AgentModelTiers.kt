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
 *
 * Override per environment via `AI_MODEL_FAST` / `AI_MODEL_SMART` / `AI_MODEL_DEEP`
 * (mapped in application.yml); the `anthropic` profile uses `ANTHROPIC_MODEL_*`.
 */
@ConfigurationProperties("agent.models")
data class AgentModelTiers(
    var fast: String = "deepseek-flash",
    var smart: String = "deepseek-flash",
    var deep: String = "deepseek-v4-pro"
)