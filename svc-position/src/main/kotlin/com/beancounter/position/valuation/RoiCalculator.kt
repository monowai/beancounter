package com.beancounter.position.valuation

import com.beancounter.common.model.MoneyValues
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Provides functionality to calculate the Return on Investment (ROI) for financial positions.
 * This class calculates ROI based on the market value, cost basis, and dividends of an investment.
 *
 * <p>The ROI is computed as the ratio of the net gains (or losses) and the dividends received
 * to the original investment cost. This provides a measure of the profitability of an investment
 * and is expressed as a percentage of the initial amount invested.</p>
 *
 * <p>Method {@code calculateROI}:
 * <ul>
 *     <li>Computes the ROI based on the provided {@link MoneyValues}, which encapsulate all
 *     relevant financial metrics of an asset.</li>
 *     <li>Returns zero if the cost value of the investment is zero to prevent division by zero errors.</li>
 *     <li>Calculates net gains as the difference between the market value and cost value of the asset,
 *     adds any dividends received, and divides this total by the cost value to derive the ROI.</li>
 *     <li>The division is performed with a precision up to 8 decimal places and uses {@link RoundingMode#HALF_UP}
 *     to round the result.</li>
 * </ul>
 * </p>
 *
 * <p>This class can be utilized in financial applications where assessing the effectiveness of investments
 * or comparing different investment opportunities is required.</p>
 */
class RoiCalculator {
    fun calculateROI(moneyValues: MoneyValues): BigDecimal {
        val costBasis = costBasis(moneyValues)

        if (costBasis.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO
        }
        val returns = moneyValues.totalGain
        return returns
            .divide(
                costBasis,
                6,
                RoundingMode.HALF_UP
            )
    }

    private fun costBasis(moneyValues: MoneyValues): BigDecimal =
        if (moneyValues.costValue.compareTo(BigDecimal.ZERO) == 0) {
            moneyValues.purchases
        } else {
            moneyValues.costValue
        }
}