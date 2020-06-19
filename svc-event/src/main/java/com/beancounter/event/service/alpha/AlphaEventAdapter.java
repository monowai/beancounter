package com.beancounter.event.service.alpha;

import com.beancounter.common.event.CorporateEvent;
import com.beancounter.common.exception.SystemException;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.input.TrustedTrnEvent;
import com.beancounter.common.model.CallerRef;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.TrnStatus;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.common.utils.MathUtils;
import com.beancounter.event.service.Event;
import com.beancounter.event.service.TaxService;
import java.math.BigDecimal;
import java.time.LocalDate;

public class AlphaEventAdapter implements Event {
  private final TaxService taxService;
  private final DateUtils dateUtils = new DateUtils();

  public AlphaEventAdapter(TaxService taxService) {
    this.taxService = taxService;
  }

  @Override
  public TrustedTrnEvent calculate(Portfolio portfolio,
                                   Position currentPosition, CorporateEvent event) {
    if (event.getTrnType().equals(TrnType.DIVI)) {
      return TrustedTrnEvent.builder()
          .portfolio(portfolio)
          .trnInput(toDividend(currentPosition, event)).build();
    }
    throw new SystemException(String.format("Unsupported event type %s", event.getTrnType()));
  }

  private TrnInput toDividend(Position currentPosition,
                              CorporateEvent corporateEvent) {
    LocalDate payDate = corporateEvent.getRecordDate().plusDays(18);

    if (payDate.compareTo(dateUtils.getDate()) > 0) {
      return null; // Don't create forward dated transactions
    }

    BigDecimal gross = calculateGross(currentPosition, corporateEvent.getRate());
    BigDecimal tax = calculateTax(currentPosition, gross);
    CallerRef callerRef = CallerRef.builder()
        .provider(corporateEvent.getSource())
        .batch(corporateEvent.getId())
        .build();
    return TrnInput.builder()
        .callerRef(callerRef)
        .trnType(TrnType.DIVI)
        .status(TrnStatus.PROPOSED)
        .quantity(currentPosition.getQuantityValues().getTotal())
        .tradeDate(payDate) // Should be PayDate +1
        .asset(corporateEvent.getAssetId())
        .price(corporateEvent.getRate())
        .tax(tax)
        .tradeCurrency(currentPosition.getAsset().getMarket().getCurrency().getCode())
        .cashCurrency(currentPosition.getAsset().getMarket().getCurrency().getCode())
        .tradeAmount(gross.subtract(tax))
        .build();
  }

  private BigDecimal calculateGross(Position currentPosition, BigDecimal rate) {
    return MathUtils.multiply(currentPosition.getQuantityValues().getTotal(), rate);
  }

  private BigDecimal calculateTax(Position currentPosition, BigDecimal gross) {
    return MathUtils.multiply(gross,
        taxService.getRate(currentPosition.getAsset().getMarket().getCurrency().getCode()));
  }
}
