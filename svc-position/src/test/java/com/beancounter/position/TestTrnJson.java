package com.beancounter.position;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Trn;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import java.io.File;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Verify that serialization works.
 *
 * @author mikeh
 * @since 2019-02-14
 */
class TestTrnJson {

  @Test
  void is_CollectionOfTradesSerializing() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    File tradeFile = new ClassPathResource("contracts/trades.json").getFile();

    CollectionType javaType = mapper.getTypeFactory()
        .constructCollectionType(Collection.class, Trn.class);

    Collection<Trn> results = mapper.readValue(tradeFile, javaType);
    assertThat(results).isNotEmpty();
  }
}
