package com.beancounter.position;

import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Transaction;
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
class TestTransactionJson {

  @Test
  void jsonSerialization() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    File tradeFile = new ClassPathResource("trades.json").getFile();

    CollectionType javaType = mapper.getTypeFactory()
        .constructCollectionType(Collection.class, Transaction.class);

    Collection<Transaction> results = mapper.readValue(tradeFile, javaType);
    assertThat(results).isNotEmpty();
  }
}
