package com.beancounter.agent.screenshot

import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.deser.std.StdDeserializer
import java.math.BigDecimal

/**
 * Reads a number the way a brokerage page prints it: `$1,415.00`, `+0.91`,
 * `(167.25)` for a loss, `12 shares` with its unit. The model copies what it
 * sees; turning that into a number stays here. Anything else, a percentage or
 * a date included, is null: the user fills that cell in on the confirmation
 * screen, and the rest of the page is still read.
 */
class PrintedNumber : StdDeserializer<BigDecimal>(BigDecimal::class.java) {
    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext
    ): BigDecimal? {
        val printed = p.valueAsString.orEmpty().trim()
        val match = PRINTED.matchEntire(printed) ?: return null
        val number = match.groupValues[1].replace(",", "").toBigDecimal()
        return if (printed.any { it in NEGATIVE }) number.negate() else number
    }

    private companion object {
        /** Optional bracket; a sign on either side of an optional currency symbol; digits; an optional unit word. */
        val PRINTED = Regex("""\(?[-+]?\$?[-+]?(\d[\d,]*(?:\.\d+)?|\.\d+)\)?(?:\s*[A-Za-z]+)?""")

        /** A loss is printed with a minus sign or in brackets. */
        const val NEGATIVE = "-("
    }
}