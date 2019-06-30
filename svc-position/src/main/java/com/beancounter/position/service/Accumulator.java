package com.beancounter.position.service;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.MoneyValues;
import com.beancounter.common.model.QuantityValues;
import com.beancounter.common.model.Transaction;
import com.beancounter.position.config.TransactionConfiguration;
import com.beancounter.position.model.Position;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


/**
 * Accumulate transactions into Positions.
 *
 * @author mikeh
 * @since 2019-02-07
 */
@Service
public class Accumulator {

  @Value("${beancounter.positions.ordered:false}")
  boolean orderedTransactions = false;

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
   * @param position    Position to accumulate the transaction into
   * @return result object
   */
  public Position accumulate(Transaction transaction, Position position) {
    boolean dateSensitive = !transactionConfiguration.isDividend(transaction);
    if (dateSensitive) {
      isDateSequential(transaction, position);
    }

    if (transactionConfiguration.isDividend(transaction)) {
      dividend(transaction, position);
    } else if (transactionConfiguration.isPurchase(transaction)) {
      buy(transaction, position);
    } else if (transactionConfiguration.isSale(transaction)) {
      sell(transaction, position);
    } else if (transactionConfiguration.isSplit(transaction)) {
      split(transaction, position);
    }
    if (dateSensitive) {
      position.setLastTradeDate(transaction.getTradeDate());
    }
    return position;
  }

  private void dividend(Transaction transaction, Position position) {
    position.getMoneyValue(Position.In.LOCAL)
        .setDividends(position.getMoneyValue(Position.In.LOCAL)
            .getDividends().add(
                transaction.getTradeAmount()));
  }

  private void isDateSequential(Transaction transaction, Position position) {
    boolean validDate = false;

    Date tradeDate = transaction.getTradeDate();
    Date positionDate = position.getLastTradeDate();

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

  private BigDecimal cost(BigDecimal costBasis, BigDecimal total) {
    return costBasis
        .divide(total, mathContext);
  }

  private void buy(Transaction transaction, Position position) {
    QuantityValues quantityValues = position.getQuantityValues();
    quantityValues.setPurchased(quantityValues.getPurchased().add(transaction.getQuantity()));
    MoneyValues moneyValues = position.getMoneyValue(Position.In.LOCAL);

    moneyValues.setPurchases(
        moneyValues.getPurchases().add(transaction.getTradeAmount()));

    moneyValues.setCostBasis(moneyValues.getCostBasis().add(transaction.getTradeAmount()));

    if (!moneyValues.getCostBasis().equals(BigDecimal.ZERO)) {

      moneyValues.setAverageCost(
          cost(moneyValues.getCostBasis(), quantityValues.getTotal())
      );

    }

    moneyValues.setCostValue(moneyValues.getAverageCost().multiply(quantityValues.getTotal())
        .setScale(2, RoundingMode.HALF_UP));
  }

  private void sell(Transaction transaction, Position position) {
    BigDecimal soldQuantity = transaction.getQuantity();
    if (soldQuantity.doubleValue() > 0) {
      // Sign the quantities
      soldQuantity = new BigDecimal(0 - transaction.getQuantity().doubleValue());
    }

    QuantityValues quantityValues = position.getQuantityValues();
    quantityValues.setSold(quantityValues.getSold().add(soldQuantity));
    MoneyValues moneyValues = position.getMoneyValue(Position.In.LOCAL);

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

    moneyValues.setCostValue(
        moneyValues.getAverageCost().multiply(quantityValues.getTotal()));

    if (quantityValues.getTotal().equals(BigDecimal.ZERO)) {
      moneyValues.setCostBasis(BigDecimal.ZERO);
      moneyValues.setCostValue(BigDecimal.ZERO);
      moneyValues.setAverageCost(BigDecimal.ZERO);
    }

  }


  private void split(Transaction transaction, Position position) {
    BigDecimal total = position.getQuantityValues().getTotal();
    position.getQuantityValues().setAdjustment(
        (transaction.getQuantity().multiply(total)).subtract(total)
    );
    position.getMoneyValue(Position.In.LOCAL)
        .setAverageCost(cost(
            position.getMoneyValue(Position.In.LOCAL).getCostBasis(),
            position.getQuantityValues().getTotal()));
  }
}
