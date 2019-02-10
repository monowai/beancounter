package com.beancounter.position.counter;

import com.beancounter.common.model.Position;
import com.beancounter.common.model.Quantity;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;


/**
 * Adds transactions into Positions.
 * @author mikeh
 * @since 2019-02-07
 */
@Service
public class Accumulator {

  /**
   * Main calculation routine.
   * @param transaction Transaction to add
   * @param position    Position to accumulate the transaction into
   * @return result object
   */
  public Position accumulate(Transaction transaction, Position position) {
    if (transaction.getTrnType().equals(TrnType.BUY)) {
      return buySide(transaction, position);
    } else if (transaction.getTrnType().equals(TrnType.SELL)) {
      return sellSide(transaction, position);
    }
    return position;
  }

  private Position buySide(Transaction transaction, Position position) {
    Quantity quantity = position.getQuantity();
    quantity.setPurchased(quantity.getPurchased().add(transaction.getQuantity()));
    position.getMoneyValues().setMarketCost(
        position.getMoneyValues().getMarketCost().add(transaction.getTradeAmount()));
    return position;
  }

  private Position sellSide(Transaction transaction, Position position) {
    BigDecimal quantitySold = transaction.getQuantity();
    if (quantitySold.doubleValue() > 0) {
      // Sign the quantity
      quantitySold = new BigDecimal(0 - transaction.getQuantity().doubleValue());
    }

    Quantity quantity = position.getQuantity();
    quantity.setSold(quantity.getSold().add(quantitySold));

    return position;
  }

}
