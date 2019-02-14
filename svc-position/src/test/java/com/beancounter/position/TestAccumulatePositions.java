package com.beancounter.position;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;

import com.beancounter.common.model.Transaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import io.restassured.http.ContentType;
import java.io.File;
import java.util.Collection;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@SpringBootTest
@ActiveProfiles("test")
@Log4j2
class TestAccumulatePositions {

  @Autowired
  private WebApplicationContext context;

  @Test
  @Tag("slow")
  void getPositionsFromTransactions() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    File tradeFile = new ClassPathResource("trades.json").getFile();

    CollectionType javaType = mapper.getTypeFactory()
        .constructCollectionType(Collection.class, Transaction.class);

    Collection<Transaction> results = mapper.readValue(tradeFile, javaType);

    given()
        .webAppContextSetup(context)
        .body(mapper.writeValueAsString(results))
        .contentType(MediaType.APPLICATION_JSON_UTF8_VALUE)
        .when()
        .post("/")
        .then()
        .log().all(true)
        .statusCode(200)
        .contentType(MediaType.APPLICATION_JSON_UTF8_VALUE)
        .extract().response();


  }

}
