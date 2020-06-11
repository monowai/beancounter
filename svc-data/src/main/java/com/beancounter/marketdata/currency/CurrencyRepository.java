package com.beancounter.marketdata.currency;

import com.beancounter.common.model.Currency;
import org.springframework.data.repository.CrudRepository;

public interface CurrencyRepository extends CrudRepository<Currency, String> {
  Iterable<Currency> findAllByOrderByCodeAsc();
}
