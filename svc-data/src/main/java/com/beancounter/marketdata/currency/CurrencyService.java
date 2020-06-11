package com.beancounter.marketdata.currency;

import com.beancounter.common.model.Currency;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Verification of Market related functions.
 *
 * @author mikeh
 * @since 2019-03-19
 */
@Component
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "beancounter.currency")
@Slf4j
@Data
public class CurrencyService {

  private final CurrencyRepository currencyRepository;

  private String base;
  Collection<Currency> values;
  private Currency baseCurrency;

  public CurrencyService(CurrencyRepository currencyRepository) {
    this.currencyRepository = currencyRepository;
  }

  @PostConstruct
  private void persist() {
    log.info("Persisting {} default currencies", getValues().size());
    Iterable<Currency> result = currencyRepository.saveAll(getValues());
    for (Currency currency : result) {
      log.debug("Persisted {}", currency);
    }
    baseCurrency = getCode(getBase()); // Default base currency
  }

  /**
   * Resolves a currency via its ISO code (AK).
   *
   * @param code non-null code
   * @return resolved currency
   */
  @Cacheable("currency.code")
  public Currency getCode(@NonNull String code) {
    Objects.requireNonNull(code);
    Optional<Currency> result = currencyRepository.findById(code.toUpperCase());
    return result.orElse(null);
  }

  public Currency getBaseCurrency() {
    return baseCurrency;
  }

  @Cacheable("currency.all")
  public Iterable<Currency> getCurrencies() {
    return currencyRepository.findAllByOrderByCodeAsc();
  }

}
