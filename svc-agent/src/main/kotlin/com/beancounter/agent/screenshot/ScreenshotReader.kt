package com.beancounter.agent.screenshot

import com.beancounter.common.utils.BcJson
import tools.jackson.core.JacksonException

/**
 * Turns a brokerage screenshot into a [ScreenshotProposal]. Stateless: it keeps
 * neither the image nor the model's reply, and logs neither.
 */
class ScreenshotReader(
    private val vision: VisionModel
) {
    /** @throws UnreadableScreenshotException when the model's reply is not a proposal */
    fun read(screenshot: Screenshot): ScreenshotProposal {
        val reply = vision.read(INSTRUCTION, screenshot)
        val proposal =
            try {
                BcJson.objectMapper.readValue(unfenced(reply), ScreenshotProposal::class.java)
            } catch (e: JacksonException) {
                throw UnreadableScreenshotException(e)
            }
        return proposal.copy(
            brokerName = proposal.brokerName?.trim()?.ifEmpty { null },
            accountNumber = proposal.accountNumber?.let(::visibleDigits)
        )
    }

    /** The reply without the Markdown code fence a model behind a gateway may wrap its JSON in. */
    private fun unfenced(reply: String): String =
        reply
            .trim()
            .removePrefix(FENCE + "json")
            .removePrefix(FENCE)
            .removeSuffix(FENCE)

    /** The last run of digits in an account label such as "Individual ...482". */
    private fun visibleDigits(label: String): String? = DIGITS.findAll(label).lastOrNull()?.value

    companion object {
        private val DIGITS = Regex("\\d+")
        private const val FENCE = "```"

        val INSTRUCTION =
            """
            You read one screenshot of a brokerage web page or phone app. Reply with JSON only, in this shape:
            {"documentType": "HOLDINGS" | "TRANSACTIONS",
             "brokerName": string | null, "accountNumber": string | null, "asOf": string | null, "totalShown": string | null,
             "rows": [{"code": string | null, "name": string | null,
                       "assetType": "EQUITY" | "ETF" | "CRYPTO" | "OPTION" | "CASH" | "OTHER",
                       "quantity": string | null, "price": string | null, "marketValue": string | null,
                       "costBasis": string | null, "costPerShare": string | null, "gainLoss": string | null,
                       "currency": string | null,
                       "tradeDate": "YYYY-MM-DD" | null, "type": string | null, "amount": string | null, "fees": string | null}]}
            documentType is HOLDINGS for a list of what is held now, TRANSACTIONS for a history of trades and cash movements.
            brokerName is the firm's name exactly as printed, without words such as "from" a parent company. accountNumber is the account label as printed, masked digits included.
            totalShown is the printed total of the listed holdings, not an account's net value or buying power.
            One row for each holding or transaction, in the order shown. code is the ticker symbol as printed.
            price is what one share is worth now; on a phone list it is often the small figure under the holding's value, with no label.
            costBasis is the total cost of a holding. costPerShare is an average cost or a price paid for one share.
            gainLoss is the total gain or loss in money, and only from a column or label that says gain or loss: never a percentage, never the day's change, never a price.
            For a transaction: tradeDate, type as printed, amount with its sign as printed, fees.
            Give every number as a string, copied exactly as displayed with its sign, for example "-1,415.00". Never calculate, round or guess a number. Use null for anything the screenshot does not show.
            """.trimIndent()
    }
}