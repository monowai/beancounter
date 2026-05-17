package com.beancounter.agent

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Anthropic model IDs per cost/quality tier.
 *
 * `fast`  — chat, lookups, list/rank queries, news triage. Default Haiku.
 * `smart` — analytical multi-step domains (retirement projections, rebalance
 *           plans, deep asset review). Default Sonnet.
 * `deep`  — reserved for queries the LLM itself escalates as ambiguous /
 *           multi-portfolio aggregation. Default Opus. Currently unused by
 *           the page-context selector — wire in when a domain genuinely needs it.
 *
 * Override per environment via env vars: `ANTHROPIC_MODEL_FAST`, `ANTHROPIC_MODEL_SMART`,
 * `ANTHROPIC_MODEL_DEEP` (mapped in application.yml).
 */
@ConfigurationProperties("agent.models")
data class AgentModelTiers(
    var fast: String = "claude-haiku-4-5-20251001",
    var smart: String = "claude-sonnet-4-6",
    var deep: String = "claude-opus-4-7"
)