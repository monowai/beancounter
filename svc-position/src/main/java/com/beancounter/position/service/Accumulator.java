package com.beancounter.position.service;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Positions;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.position.accumulation.Buy;
import com.beancounter.position.accumulation.Dividend;
import com.beancounter.position.accumulation.Sell;
import com.beancounter.position.accumulation.Split;
import com.beancounter.position.accumulation.ValueTransaction;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;


/**
 * Convenience service to apply the correct AccumulationLogic to the transaction
 * and calculate the value of a position.
 *
 * @author mikeh
 * @since 2019-02-07
 */
@Service
public class Accumulator {

  //@Value("${beancounter.positions.ordered:false}")
  //private boolean orderedTransactions = false;

  private Map<TrnType, ValueTransaction> logicMap = new HashMap<>();

  public Accumulator() {
    logicMap.put(TrnType.BUY, new Buy());
    logicMap.put(TrnType.SELL, new Sell());
    logicMap.put(TrnType.DIVI, new Dividend());
    logicMap.put(TrnType.SPLIT, new Split());
  }

  Position accumulate(Transaction transaction, Positions positions) {
    return accumulate(transaction, positions.getPortfolio(),
        positions.get(transaction.getAsset(), transaction.getTradeDate()));
  }

  /**
   * Main calculation routine.
   *
   * @param transaction Transaction to add
   * @param position    Position to accumulate the transaction into
   * @return result object
   */
  public Position accumulate(Transaction transaction, Portfolio portfolio, Position position) {
    boolean dateSensitive = (transaction.getTrnType() != TrnType.DIVI);
    if (dateSensitive) {
      isDateSequential(transaction, position);
    }
    ValueTransaction valueTransaction = logicMap.get(transaction.getTrnType());
    valueTransaction.value(transaction, portfolio, position);
    if (dateSensitive) {
      position.getDateValues().setLast(DateUtils.getDateString(transaction.getTradeDate()));
    }

    return position;
  }


  private void isDateSequential(Transaction transaction, Position position) {
    boolean validDate = false;

    LocalDate tradeDate = transaction.getTradeDate();
    LocalDate positionDate = DateUtils.getDate(position.getDateValues().getLast());

    if (positionDate == null || (positionDate.compareTo(tradeDate) <= 0)) {
      validDate = true;
    }

    if (!validDate) {
      throw new BusinessException(String.format("Date sequence problem %s",
          transaction.toString()));
    }
  }


}
