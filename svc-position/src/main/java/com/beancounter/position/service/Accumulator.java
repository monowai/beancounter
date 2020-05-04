package com.beancounter.position.service;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.Positions;
import com.beancounter.common.model.Trn;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.position.accumulation.AccumulationStrategy;
import com.beancounter.position.accumulation.BuyBehaviour;
import com.beancounter.position.accumulation.DividendBehaviour;
import com.beancounter.position.accumulation.SellBehaviour;
import com.beancounter.position.accumulation.SplitBehaviour;
import com.beancounter.position.accumulation.TrnBehaviourFactory;
import java.time.LocalDate;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;


/**
 * Convenience service to apply the correct AccumulationLogic to the transaction
 * and calculate the value of a position.
 *
 * @author mikeh
 * @since 2019-02-07
 */
@Service
@Import({
    DateUtils.class,
    TrnBehaviourFactory.class,
    BuyBehaviour.class,
    SellBehaviour.class,
    DividendBehaviour.class,
    SplitBehaviour.class})
public class Accumulator {
  private final DateUtils dateUtils = new DateUtils();
  private final TrnBehaviourFactory trnBehaviourFactory;

  public Accumulator(TrnBehaviourFactory trnBehaviourFactory) {
    this.trnBehaviourFactory = trnBehaviourFactory;
  }

  Position accumulate(Trn trn, Positions positions) {
    return accumulate(trn, positions.getPortfolio(),
        positions.get(
            trn.getAsset(),
            trn.getTradeDate())
    );
  }

  /**
   * Main calculation routine.
   *
   * @param trn      Transaction to add
   * @param position Position to accumulate the transaction into
   * @return result object
   */
  public Position accumulate(Trn trn, Portfolio portfolio, Position position) {
    boolean dateSensitive = (trn.getTrnType() != TrnType.DIVI);
    if (dateSensitive) {
      isDateSequential(trn, position);
    }
    AccumulationStrategy accumulationStrategy = trnBehaviourFactory.get(trn.getTrnType());
    accumulationStrategy.accumulate(trn, portfolio, position);
    if (dateSensitive) {
      position.getDateValues().setLast(dateUtils.getDateString(trn.getTradeDate()));
    }

    return position;
  }


  private void isDateSequential(Trn trn, Position position) {
    boolean validDate = false;

    LocalDate tradeDate = trn.getTradeDate();
    LocalDate positionDate = dateUtils.getDate(position.getDateValues().getLast());

    if (positionDate == null || (positionDate.compareTo(tradeDate) <= 0)) {
      validDate = true;
    }

    if (!validDate) {
      throw new BusinessException(String.format("Date sequence problem %s",
          trn.toString()));
    }
  }


}
