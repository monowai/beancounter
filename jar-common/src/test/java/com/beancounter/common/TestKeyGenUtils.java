package com.beancounter.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.utils.KeyGenUtils;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class TestKeyGenUtils {

  @Test
  void is_uuidGenerating() {
    UUID uuid = UUID.randomUUID();
    String webSafe = KeyGenUtils.format(uuid);
    assertThat(webSafe).isNotNull();
    assertThat(KeyGenUtils.parse(webSafe).compareTo(uuid)).isEqualTo(0);
    assertThat(KeyGenUtils.parse(uuid.toString()).compareTo(uuid)).isEqualTo(0);
  }

  @Test
  void is_ArgumentExceptionsCorrect() {
    assertThrows(BusinessException.class, () -> KeyGenUtils.format(null));
    assertThrows(BusinessException.class, () -> KeyGenUtils.parse(null));
    assertThrows(BusinessException.class, () -> KeyGenUtils.parse("ABC"));
    assertThrows(BusinessException.class, () -> KeyGenUtils.parse("12345678901234567"));
    assertThrows(BusinessException.class, () -> KeyGenUtils.parse(""));
  }
}
