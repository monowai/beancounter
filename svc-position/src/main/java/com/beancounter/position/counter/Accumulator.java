package com.beancounter.position.counter;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.QuantityValues;
import com.beancounter.common.model.Transaction;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


/**
 * Adds transactions into Positions.
 *
 * @author mikeh
 * @since 2019-02-07
 */
@Service
public class Accumulator {

  private TransactionConfiguration transactionConfiguration;
  private MathContext mathContext = new MathContext(10);

  @Autowired
  public Accumulator(TransactionConfiguration transactionConfiguration) {
    this.transactionConfiguration = transactionConfiguration;
  }

  /**
   * Main calculation routine.
   *
   * @param transaction Transaction to add
   * @param position Position to accumulate the transaction into
   * @return result object
   */
  public Position accumulate(Transaction transaction, Position position) {

    isTransactionValid(transaction, position);

    if (transactionConfiguration.isPurchase(transaction)) {
      accumulateBuy(transaction, position);
    } else if (transactionConfiguration.isSale(transaction)) {
      accumulateSell(transaction, position);
    } else if (transactionConfiguration.isSplit(transaction)) {
      handleSplit(transaction, position);
    }
    position.setLastDate(transaction.getTradeDate());
    return position;
  }

  private void isTransactionValid(Transaction transaction, Position position) {
    boolean validDate = false;

    Date tradeDate = transaction.getTradeDate();
    Date positionDate = position.getLastDate();

    if (positionDate == null) {
      validDate = true;
    } else if (positionDate.compareTo(tradeDate) <= 0) {
      validDate = true;
    }

    if (!validDate) {
      throw new BusinessException(String.format("Date sequence problem %s",
          transaction.toString()));
    }
  }

  private BigDecimal getAverageCost(BigDecimal costBasis, BigDecimal total) {
    return costBasis
        .divide(total, mathContext);
  }

  private void accumulateBuy(Transaction transaction, Position position) {
    QuantityValues quantityValues = position.getQuantityValues();
    quantityValues.setPurchased(quantityValues.getPurchased().add(transaction.getQuantity()));
    MoneyValues moneyValues = position.getMoneyValues();

    moneyValues.setMarketCost(
        moneyValues.getMarketCost().add(transaction.getTradeAmount()));

    moneyValues.setPurchases(
        moneyValues.getPurchases().add(transaction.getTradeAmount()));

    moneyValues.setCostBasis(moneyValues.getCostBasis().add(transaction.getTradeAmount()));

    if (!moneyValues.getCostBasis().equals(BigDecimal.ZERO)) {

      moneyValues.setAverageCost(
          getAverageCost(moneyValues.getCostBasis(), quantityValues.getTotal())
      );

    }

  }

  private void accumulateSell(Transaction transaction, Position position) {
    BigDecimal soldQuantity = transaction.getQuantity();
    if (soldQuantity.doubleValue() > 0) {
      // Sign the quantities
      soldQuantity = new BigDecimal(0 - transaction.getQuantity().doubleValue());
    }

    QuantityValues quantityValues = position.getQuantityValues();
    quantityValues.setSold(quantityValues.getSold().add(soldQuantity));
    MoneyValues moneyValues = position.getMoneyValues();

    moneyValues.setSales(
        moneyValues.getSales().add(transaction.getTradeAmount()));

    if (!transaction.getTradeAmount().equals(BigDecimal.ZERO)) {
      BigDecimal tradeCost = transaction.getTradeAmount()
          .divide(transaction.getQuantity().abs(), mathContext);
      BigDecimal unitProfit = tradeCost.subtract(moneyValues.getAverageCost());
      BigDecimal realisedGain = unitProfit.multiply(transaction.getQuantity().abs());

      moneyValues.setRealisedGain(
          moneyValues.getRealisedGain()
              .add(realisedGain).setScale(2, RoundingMode.HALF_UP)
      );
    }

    if (quantityValues.getTotal().equals(BigDecimal.ZERO)) {
      moneyValues.setCostBasis(BigDecimal.ZERO);
      moneyValues.setAverageCost(BigDecimal.ZERO);
    }

  }


  private void handleSplit(Transaction transaction, Position position) {
    BigDecimal total = position.getQuantityValues().getTotal();
    position.getQuantityValues().setAdjustment(
        (transaction.getQuantity().multiply(total)).subtract(total)
    );
    position.getMoneyValues()
        .setAverageCost(getAverageCost(
            position.getMoneyValues().getCostBasis(),
            position.getQuantityValues().getTotal()));
  }
}
