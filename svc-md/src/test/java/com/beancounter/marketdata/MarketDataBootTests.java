package com.beancounter.marketdata;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.MarketData;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@WebAppConfiguration
@ActiveProfiles("test")
class MarketDataBootTests {

  @Autowired
  private WebApplicationContext context;


  @Test
  void contextLoads() {
  }

  @Test
  @Tag("slow")
  void getMarketData() {
    Asset asset = Asset.builder().code("dummy").build();
    MarketData mdResponse = given()
        .webAppContextSetup(context)
        .log().all()
        .when()
        .get("/{assetId}", asset.getCode())
        .then()
        .log().all(true)
        .statusCode(200)
        .contentType(MediaType.APPLICATION_JSON_UTF8_VALUE)
        .extract().response().as(MarketData.class);

    assertThat(mdResponse.getAsset()).isEqualTo(asset);
    assertThat(mdResponse.getOpen()).isEqualTo(BigDecimal.valueOf(999.99));


  }

  @Test
  @Tag("slow")
  void getCollectionOfMarketData() {
    Collection<Asset> assets = new ArrayList<>();
    Asset asset = Asset.builder().code("assetCode")
        .market(Market.builder().code("marketCode").build()).build();
    
    assets.add(asset);

    List<MarketData> mdResponse = given()
        .body(assets)
        .contentType(MediaType.APPLICATION_JSON_UTF8_VALUE)
        .webAppContextSetup(context)
        .log().all()
        .when()
        .post("/")
        .then()
        .log().all(true)
        .statusCode(200)
        .contentType(MediaType.APPLICATION_JSON_UTF8_VALUE)
        .extract()
        .body().jsonPath().getList(".", MarketData.class);

    assertThat(mdResponse).hasSize(assets.size());
  }


}


