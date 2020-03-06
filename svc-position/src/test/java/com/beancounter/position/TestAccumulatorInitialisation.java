package com.beancounter.position;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.TrnType;
import com.beancounter.position.accumulation.TrnBehaviourFactory;
import com.beancounter.position.service.Accumulator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = Accumulator.class)
public class TestAccumulatorInitialisation {
  @Autowired
  private Accumulator accumulator;
  @Autowired
  private TrnBehaviourFactory trnBehaviourFactory;

  @Test
  void is_AccumulatorInitialising() {
    assertThat(accumulator).isNotNull();
    assertThat(trnBehaviourFactory).isNotNull();
    assertThat(trnBehaviourFactory.get(TrnType.BUY)).isNotNull();
    assertThat(trnBehaviourFactory.get(TrnType.SELL)).isNotNull();
    assertThat(trnBehaviourFactory.get(TrnType.DIVI)).isNotNull();
    assertThat(trnBehaviourFactory.get(TrnType.SPLIT)).isNotNull();
  }
}
