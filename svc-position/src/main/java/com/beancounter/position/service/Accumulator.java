package com.beancounter.position.service;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Positions;
import com.beancounter.common.model.Trn;
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

  Position accumulate(Trn trn, Positions positions) {
    return accumulate(trn, positions.getPortfolio(),
        positions.get(trn.getAsset(), trn.getTradeDate()));
  }

  /**
   * Main calculation routine.
   *
   * @param trn Transaction to add
   * @param position    Position to accumulate the transaction into
   * @return result object
   */
  public Position accumulate(Trn trn, Portfolio portfolio, Position position) {
    boolean dateSensitive = (trn.getTrnType() != TrnType.DIVI);
    if (dateSensitive) {
      isDateSequential(trn, position);
    }
    ValueTransaction valueTransaction = logicMap.get(trn.getTrnType());
    valueTransaction.value(trn, portfolio, position);
    if (dateSensitive) {
      position.getDateValues().setLast(DateUtils.getDateString(trn.getTradeDate()));
    }

    return position;
  }


  private void isDateSequential(Trn trn, Position position) {
    boolean validDate = false;

    LocalDate tradeDate = trn.getTradeDate();
    LocalDate positionDate = DateUtils.getDate(position.getDateValues().getLast());

    if (positionDate == null || (positionDate.compareTo(tradeDate) <= 0)) {
      validDate = true;
    }

    if (!validDate) {
      throw new BusinessException(String.format("Date sequence problem %s",
          trn.toString()));
    }
  }


}
