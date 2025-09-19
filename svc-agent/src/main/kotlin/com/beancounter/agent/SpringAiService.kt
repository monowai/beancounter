package com.beancounter.agent

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Service

/**
 * Service for integrating with Spring AI for contextual analysis
 * Only created when ChatClient is available
 */
@Service
@ConditionalOnBean(ChatClient::class)
class SpringAiService
    @Autowired
    constructor(
        private val chatClient: ChatClient
    ) {
        private val log = LoggerFactory.getLogger(SpringAiService::class.java)

        /**
         * Generate contextual analysis using Spring AI
         */
        fun generateAnalysis(
            query: String,
            portfolioData: Map<String, Any>,
            analysisType: String
        ): String =
            try {
                log.info("Generating Spring AI analysis for type: {}", analysisType)

                val prompt = buildAnalysisPrompt(query, portfolioData, analysisType)
                val response =
                    chatClient
                        .prompt()
                        .user(prompt)
                        .call()
                        .content() ?: "No response generated"

                log.debug("Spring AI analysis generated successfully")
                response
            } catch (e: Exception) {
                log.error("Error generating Spring AI analysis: {}", e.message, e)
                generateFallbackAnalysis(analysisType, e.message ?: "Unknown error")
            }

        /**
         * Check if Spring AI is properly configured
         */
        fun isConfigured(): Boolean =
            try {
                // Try a simple test call
                chatClient
                    .prompt()
                    .user("Test")
                    .call()
                    .content()
                true
            } catch (e: Exception) {
                log.warn("Spring AI not properly configured: {}", e.message)
                false
            }

        /**
         * Build a contextual prompt for the LLM
         */
        private fun buildAnalysisPrompt(
            query: String,
            portfolioData: Map<String, Any>,
            analysisType: String
        ): String =
            buildString {
                appendLine(
                    "You are a knowledgeable financial analyst with expertise in portfolio analysis and market insights."
                )
                appendLine()
                appendLine("**User Query:** $query")
                appendLine()
                appendLine("**Analysis Type:** $analysisType")
                appendLine()
                appendLine("**Portfolio Data:**")
                appendLine(formatPortfolioData(portfolioData))
                appendLine()
                appendLine("**Instructions:**")
                when (analysisType) {
                    "get_largest_holdings" -> {
                        appendLine("- Analyze the largest holdings and their concentration risk")
                        appendLine("- Provide insights on portfolio diversification")
                        appendLine("- Suggest any rebalancing considerations")
                        appendLine("- Highlight any potential risks or opportunities")
                    }
                    "get_position_news" -> {
                        appendLine("- Analyze recent news and events affecting the positions")
                        appendLine("- Identify potential market impacts")
                        appendLine("- Highlight any significant developments")
                        appendLine("- Assess the implications for portfolio strategy")
                    }
                    "get_top_movers" -> {
                        appendLine("- Analyze the performance drivers")
                        appendLine("- Identify trends and patterns")
                        appendLine("- Provide context for the price movements")
                        appendLine("- Suggest any tactical adjustments")
                    }
                    "analyze_portfolio_performance" -> {
                        appendLine("- Provide overall portfolio performance analysis")
                        appendLine("- Identify strengths and weaknesses")
                        appendLine("- Suggest optimization opportunities")
                        appendLine("- Compare against relevant benchmarks if possible")
                    }
                    "get_corporate_actions" -> {
                        appendLine("- Analyze the impact of corporate actions on portfolio positions")
                        appendLine("- Provide timing considerations for any actions")
                        appendLine("- Suggest portfolio adjustments if needed")
                        appendLine("- Highlight tax implications where relevant")
                    }
                    "get_upcoming_events" -> {
                        appendLine("- Analyze upcoming events and their potential impact")
                        appendLine("- Provide strategic positioning recommendations")
                        appendLine("- Suggest risk management considerations")
                        appendLine("- Highlight any time-sensitive opportunities")
                    }
                    "get_market_events" -> {
                        appendLine("- Analyze market-wide events and their implications")
                        appendLine("- Provide sector and market outlook insights")
                        appendLine("- Suggest portfolio positioning strategies")
                        appendLine("- Highlight any emerging opportunities or risks")
                    }
                    "fx_rates_analysis" -> {
                        appendLine("- Analyze the current foreign exchange rates and their implications")
                        appendLine("- Provide context on recent currency movements and trends")
                        appendLine("- Discuss potential impact on international investments")
                        appendLine("- Suggest considerations for currency hedging or exposure")
                        appendLine("- Highlight any economic factors affecting the exchange rates")
                        appendLine("- Provide practical insights for currency conversion decisions")
                    }
                    else -> {
                        appendLine("- Provide comprehensive analysis of the portfolio data")
                        appendLine("- Highlight key insights and trends")
                        appendLine("- Suggest actionable recommendations")
                        appendLine("- Consider both short-term and long-term implications")
                    }
                }
                appendLine()
                appendLine(
                    "Please provide a clear, actionable analysis in markdown format with specific insights and recommendations."
                )
            }

        /**
         * Format portfolio data for the prompt
         */
        private fun formatPortfolioData(data: Map<String, Any>): String =
            buildString {
                data.forEach { (key, value) ->
                    appendLine("**$key:**")
                    when (value) {
                        is List<*> -> {
                            value.take(5).forEach { item ->
                                appendLine("- $item")
                            }
                            if (value.size > 5) {
                                appendLine("- ... and ${value.size - 5} more items")
                            }
                        }
                        is Map<*, *> -> {
                            value.forEach { (k, v) ->
                                appendLine("  - $k: $v")
                            }
                        }
                        else -> {
                            appendLine("$value")
                        }
                    }
                    appendLine()
                }
            }

        /**
         * Generate fallback analysis when Spring AI is not available
         */
        private fun generateFallbackAnalysis(
            analysisType: String,
            errorMessage: String
        ): String =
            buildString {
                appendLine("## 🤖 AI Analysis")
                appendLine()
                appendLine("> **Note:** Spring AI integration encountered an issue: $errorMessage")
                appendLine()
                appendLine("**Fallback Analysis:**")
                when (analysisType) {
                    "get_largest_holdings" -> {
                        appendLine("- Review your largest holdings for concentration risk")
                        appendLine("- Consider diversification if any single position exceeds 10-15% of portfolio")
                        appendLine("- Monitor these positions closely for performance and news")
                    }
                    "get_position_news" -> {
                        appendLine("- Recent news may impact position valuations")
                        appendLine("- Monitor for significant developments that could affect your holdings")
                        appendLine("- Consider the timing of any major corporate actions")
                    }
                    "get_top_movers" -> {
                        appendLine("- Top movers indicate recent market sentiment changes")
                        appendLine("- Analyze whether movements are based on fundamentals or speculation")
                        appendLine("- Consider if any positions need rebalancing based on performance")
                    }
                    "analyze_portfolio_performance" -> {
                        appendLine("- Review overall portfolio performance against benchmarks")
                        appendLine("- Identify underperforming positions that may need attention")
                        appendLine("- Consider rebalancing if allocations have drifted significantly")
                    }
                    "get_corporate_actions" -> {
                        appendLine("- Corporate actions can significantly impact position values")
                        appendLine("- Monitor ex-dates and record dates for timing considerations")
                        appendLine("- Consider the tax implications of any corporate actions")
                    }
                    "get_upcoming_events" -> {
                        appendLine("- Plan ahead for upcoming events that may affect your portfolio")
                        appendLine("- Consider position sizing around major announcements")
                        appendLine("- Monitor market-wide events for broader impact assessment")
                    }
                    "get_market_events" -> {
                        appendLine("- Market events can create both opportunities and risks")
                        appendLine("- Consider how broader market trends affect your portfolio")
                        appendLine("- Stay informed about economic indicators and policy changes")
                    }
                    else -> {
                        appendLine("- Review the data above for actionable insights")
                        appendLine("- Consider consulting with a financial advisor for complex decisions")
                        appendLine("- Monitor market conditions and adjust strategy as needed")
                    }
                }
                appendLine()
                appendLine(
                    "To enable full AI analysis, ensure Spring AI is properly configured with a valid LLM provider."
                )
            }
    }