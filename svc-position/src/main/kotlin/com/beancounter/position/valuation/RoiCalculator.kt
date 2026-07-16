package com.beancounter.position.valuation

import com.beancounter.common.model.MoneyValues
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Provides functionality to calculate the Return on Investment (ROI) for financial positions.
 * This class calculates ROI based on the market value, cost basis, and dividends of an investment.
 *
 * <p>The ROI is the total return (realised gain + unrealised gain + dividends) expressed as a
 * ratio of the total capital deployed over the life of the position. This is a money-weighted
 * "total return on cost" and pairs with the annualised IRR reported alongside it.</p>
 *
 * <p>Method {@code calculateROI}:
 * <ul>
 *     <li>Computes the ROI based on the provided {@link MoneyValues}, which encapsulate all
 *     relevant financial metrics of an asset.</li>
 *     <li>Divides {@code totalGain} by {@code purchases} — the cumulative cost of every acquisition,
 *     which is never reduced by sells. This keeps the numerator (gains on both sold and held shares)
 *     and the denominator (cost of all shares ever bought) on the same scope, so partially-sold
 *     positions are not inflated by dividing lifetime gains by only the residual holding cost.</li>
 *     <li>Returns zero when there are no purchases, to prevent division by zero.</li>
 *     <li>The division is performed to 6 decimal places using {@link RoundingMode#HALF_UP}.</li>
 * </ul>
 * </p>
 *
 * <p>This class can be utilized in financial applications where assessing the effectiveness of investments
 * or comparing different investment opportunities is required.</p>
 */
class RoiCalculator {
    fun calculateROI(moneyValues: MoneyValues): BigDecimal = calculate(moneyValues.totalGain, moneyValues.purchases)

    private fun calculate(
        returns: BigDecimal,
        costBasis: BigDecimal
    ): BigDecimal {
        if (costBasis.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO
        }
        return returns
            .divide(
                costBasis,
                6,
                RoundingMode.HALF_UP
            )
    }
}