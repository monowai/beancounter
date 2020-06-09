package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.utils.KafkaUtils;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.KafkaListenerErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.ErrorMessage;

public class TestKafkaUtils {

  @Test
  void is_ErrorCovered() {
    KafkaUtils kafkaUtils = new KafkaUtils();
    ListenerExecutionFailedException failedException
        = new ListenerExecutionFailedException("Blah");
    String result = kafkaUtils.findBcCause(failedException);
    assertThat(result.contains("com.beancounter"));
    KafkaListenerErrorHandler handler = kafkaUtils.bcErrorHandler();
    Message<?> message = new ErrorMessage(failedException);
    Object errorResult = handler.handleError(message, failedException);
    assertThat(errorResult).isNull();
  }

}
