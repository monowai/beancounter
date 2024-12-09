package com.beancounter.position.accumulation

import com.beancounter.common.model.TrnType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Are known Trn behaviours wiring correctly and accessible from the factory?
 */
@SpringBootTest(classes = [Accumulator::class])
class TrnBehaviourFactoryTest {
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
        assertThat(trnBehaviourFactory[TrnType.DEPOSIT]).isNotNull
        assertThat(trnBehaviourFactory[TrnType.FX_BUY]).isNotNull
        assertThat(trnBehaviourFactory[TrnType.BALANCE]).isNotNull
    }
}