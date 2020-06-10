package com.beancounter.position.accumulation;

import com.beancounter.common.model.TrnType;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TrnBehaviourFactory {

  private final Map<TrnType, AccumulationStrategy> trnBehaviours = new HashMap<>();

  @Autowired(required = false)
  void setBuyBehaviour(BuyBehaviour buyBehaviour) {
    trnBehaviours.put(TrnType.BUY, buyBehaviour);
  }

  @Autowired(required = false)
  void setSellBehaviour(SellBehaviour sellBehaviour) {
    trnBehaviours.put(TrnType.SELL, sellBehaviour);
  }

  @Autowired(required = false)
  void setSplitBehaviour(SplitBehaviour splitBeahviour) {
    trnBehaviours.put(TrnType.SPLIT, splitBeahviour);
  }

  @Autowired(required = false)
  void setDividendBehaviour(DividendBehaviour dividendBeahviour) {
    trnBehaviours.put(TrnType.DIVI, dividendBeahviour);
  }

  public AccumulationStrategy get(TrnType trnType) {
    return trnBehaviours.get(trnType);
  }
}
