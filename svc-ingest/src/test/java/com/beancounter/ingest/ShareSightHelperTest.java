package com.beancounter.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.ingest.config.ExchangeConfig;
import com.beancounter.ingest.sharesight.ShareSightDivis;
import com.beancounter.ingest.sharesight.ShareSightHelper;
import com.beancounter.ingest.sharesight.ShareSightTrades;
import com.beancounter.ingest.sharesight.ShareSightTransformers;
import com.google.common.annotations.VisibleForTesting;
import java.math.BigDecimal;
import java.text.ParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {
    ExchangeConfig.class,
    ShareSightTransformers.class,
    ShareSightTrades.class,
    ShareSightDivis.class,
    ShareSightHelper.class})
class ShareSightHelperTest {

  @Autowired
  private ShareSightHelper shareSightHelper;

  @Test
  @VisibleForTesting
  void is_DoubleValueInputCorrect() throws ParseException {
    assertThat(shareSightHelper.parseDouble("5,000.99"))
        .isEqualByComparingTo(BigDecimal.valueOf(5000.99));
  }

  @Test
  @VisibleForTesting
  void is_ExceptionThrownResolvingIncorrectAssetCodes() {
    assertThrows(BusinessException.class, ()
        -> shareSightHelper.resolveAsset(null));
    assertThrows(BusinessException.class, ()
        -> shareSightHelper.resolveAsset("ValueWithNoSeparator"));
  }

  @Test
  @VisibleForTesting
  void is_ExchangeAliasReturnedInAssetCode() {
    Asset expectedAsset = Asset.builder()
        .code("ABBV")
        .market(Market.builder().code("NYSE").build())
        .build();

    verifyAsset("ABBV.NYSE", expectedAsset);

    expectedAsset = Asset.builder()
        .code("AMP")
        .market(Market.builder().code("ASX").build())
        .build();

    verifyAsset("AMP.AX", expectedAsset);
  }

  @Test
  @VisibleForTesting
  void is_IgnoreRatesDefaultCorrect() {
    assertThat(shareSightHelper.isRatesIgnored()).isFalse();
  }

  private void verifyAsset(String code, Asset expectedAsset) {

    Asset asset = shareSightHelper.resolveAsset(code);

    assertThat(asset)
        .isEqualToComparingFieldByField(expectedAsset);
  }
}
