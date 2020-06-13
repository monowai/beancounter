package com.beancounter.event.service.alpha;

import com.beancounter.common.exception.SystemException;
import com.beancounter.common.identity.CallerRef;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.input.TrustedTrnEvent;
import com.beancounter.common.model.CorporateEvent;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Position;
import com.beancounter.common.model.TrnStatus;
import com.beancounter.common.model.TrnType;
import com.beancounter.common.utils.MathUtils;
import com.beancounter.event.service.Event;
import com.beancounter.event.service.TaxService;
import java.math.BigDecimal;

public class AlphaEventAdapter implements Event {
  private final TaxService taxService;

  public AlphaEventAdapter(TaxService taxService) {
    this.taxService = taxService;
  }

  @Override
  public TrustedTrnEvent generate(Portfolio portfolio,
                                  Position currentPosition, CorporateEvent corporateEvent) {
    if (corporateEvent.getTrnType().equals(TrnType.DIVI)) {
      return TrustedTrnEvent.builder()
          .portfolio(portfolio)
          .trnInput(toDividend(currentPosition, corporateEvent)).build();
    }
    throw new SystemException(String.format("Unsupported event type %s",
        corporateEvent.getTrnType()));
  }

  private TrnInput toDividend(Position currentPosition,
                         CorporateEvent corporateEvent) {
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
        .tradeDate(corporateEvent.getRecordDate().plusDays(18)) // Should be PayDate +1
        .asset(corporateEvent.getAsset().getId())
        .price(corporateEvent.getRate())
        .tax(tax)
        .tradeCurrency(corporateEvent.getAsset().getMarket().getCurrency().getCode())
        .cashCurrency(corporateEvent.getAsset().getMarket().getCurrency().getCode())
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
