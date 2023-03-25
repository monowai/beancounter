package com.beancounter.common.input

import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.TrnType

/**
 * Contract to write transactions, of a format, into BC for the requested Portfolio.
 * Ownership of the portfolio is not verified, making this a trusted request.
 *
 * Message is optional and will be output to the log when the event is processed
 */
data class TrustedTrnEvent(
    override val portfolio: Portfolio,
    override val importFormat: ImportFormat = ImportFormat.BC,
    override val message: String = "",
    val trnInput: TrnInput = TrnInput(trnType = TrnType.IGNORE),
) :
    TrnImport {
    constructor(portfolio: Portfolio, trnInput: TrnInput) : this(
        portfolio = portfolio,
        importFormat = ImportFormat.BC,
        trnInput = trnInput,
    )
}
