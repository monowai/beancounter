package com.beancounter.googled.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.googled.config.ExchangeConfig;
import com.beancounter.googled.format.ShareSightHelper;
import java.math.BigDecimal;
import java.text.ParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {ExchangeConfig.class, ShareSightHelper.class})
class ShareSightHelperTest {

  @Autowired
  private ShareSightHelper helper;

  @Test
  void testDoubleHelper() throws ParseException {
    assertThat(helper.parseDouble("5,000.99"))
        .isEqualByComparingTo(BigDecimal.valueOf(5000.99));
  }

  @Test
  void verifyMarketExceptions() {
    assertThrows(BusinessException.class, () -> helper.resolveAsset(null));
    assertThrows(BusinessException.class, () -> helper.resolveAsset("ValueWithNoSeparator"));
  }

  @Test
  void marketCodeTest() {
    Asset expectedAsset = Asset.builder()
        .code("ABBV")
        .market(Market.builder().code("NYSE").build())
        .build();

    verifyAsset("ABBV.NYS", expectedAsset);
    verifyAsset("ABBV:NYS", expectedAsset);
    verifyAsset("ABBV-NYS", expectedAsset);

    expectedAsset = Asset.builder()
        .code("AMP")
        .market(Market.builder().code("AX").build())
        .build();

    verifyAsset("AMP.AX", expectedAsset);
  }

  private void verifyAsset(String code, Asset expectedAsset) {

    Asset asset = helper.resolveAsset(code);

    assertThat(asset)
        .isEqualToComparingFieldByField(expectedAsset);
  }
}
