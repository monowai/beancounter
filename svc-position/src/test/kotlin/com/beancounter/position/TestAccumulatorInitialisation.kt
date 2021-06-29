package com.beancounter.position

import com.beancounter.common.model.TrnType
import com.beancounter.position.accumulation.TrnBehaviourFactory
import com.beancounter.position.service.Accumulator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Are known Trn behaviours wiring correctly and accessible from the factory?
 */
@SpringBootTest(classes = [Accumulator::class])
class TestAccumulatorInitialisation {
    @Autowired
    private lateinit var accumulator: Accumulator

    @Autowired
    private lateinit var trnBehaviourFactory: TrnBehaviourFactory

    @Test
    fun is_AccumulatorInitialising() {
        assertThat(accumulator).isNotNull
        assertThat(trnBehaviourFactory).isNotNull
        assertThat(trnBehaviourFactory[TrnType.BUY]).isNotNull
        assertThat(trnBehaviourFactory[TrnType.SELL]).isNotNull
        assertThat(trnBehaviourFactory[TrnType.DIVI]).isNotNull
        assertThat(trnBehaviourFactory[TrnType.SPLIT]).isNotNull
    }
}
