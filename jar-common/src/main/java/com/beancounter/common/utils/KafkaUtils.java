package com.beancounter.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.listener.KafkaListenerErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@ConditionalOnProperty(value = "kafka.enabled")
public class KafkaUtils {
  public String findBcCause(ListenerExecutionFailedException e) {
    StackTraceElement[] stackTrace = e.getMostSpecificCause().getStackTrace();
    for (StackTraceElement stackTraceElement : stackTrace) {
      if (stackTraceElement.getClassName().contains("com.beancounter")) {
        return stackTraceElement.toString();
      }
    }
    return "No BC Classes Found";
  }

  @Bean
  public KafkaListenerErrorHandler bcErrorHandler() {
    return (m, e) -> {
      log.error("{}. {}. {}",
          e.getMostSpecificCause().getMessage(),
          findBcCause(e),
          m.getPayload());
      return null;
    };
  }

}
