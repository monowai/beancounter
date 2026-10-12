package com.beancounter.agent

/** Single source of truth for the `agent.provider` property and its values. */
object AgentProvider {
    const val PROPERTY = "agent.provider"
    const val DEEPSEEK = "deepseek"
    const val ANTHROPIC = "anthropic"
    const val OPENAI = "openai"
    const val OLLAMA = "ollama"

    /** Provider used when [PROPERTY] is absent. Must match `agent.provider` in application.yml. */
    const val DEFAULT = DEEPSEEK
}