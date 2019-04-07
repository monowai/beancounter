package com.beancounter.marketdata;

import com.beancounter.common.model.Market;
import com.beancounter.marketdata.providers.mock.MockProviderService;
import com.beancounter.marketdata.service.MarketConfig;
import com.beancounter.marketdata.service.MarketService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Java6Assertions.assertThat;

/**
 * Market Mapping.
 *
 * @author mikeh
 * @since 2019-03-19
 */
@SpringBootTest(classes = {MarketService.class, MarketConfig.class})
@Slf4j
class TestMarkets {

    @Autowired
    private MarketConfig marketConfig;

    @Autowired
    private MarketService marketService;

    @Test
    void markets() {

        assertThat(marketConfig).isNotNull();
        Market market = marketService.getMarket(MockProviderService.ID);
        assertThat(market)
                .isNotNull()
                .hasFieldOrPropertyWithValue("timezone", TimeZone.getTimeZone(UTC));

    }

    @Test
    void computeMarketDataProvider_PriceDateFromUserTz () {

        //  The java.util.Date has no concept of time zone, and only represents the number of seconds passed
        //  since the Unix epoch time – 1970-01-01T00:00:00Z.
        //  But, if you print the Date object directly, the Date object will be always printed with the default
        //  system time zone.

        String DATE_FORMAT = "dd-M-yyyy hh:mm:ss a";
        String dateInString = "07-4-2018 10:30:00 AM";
        // Users requested date "today in timezone"
        LocalDateTime sunday = LocalDateTime.parse(dateInString, DateTimeFormatter.ofPattern(DATE_FORMAT));
        Market sgTz = marketService.getMarket("SGX");
        ZonedDateTime usersTimeZone = sunday.atZone(sgTz.getTimezone().toZoneId());
        LocalDateTime marketProviderDate = marketService.getLastMarketDate(usersTimeZone, marketService.getMarket("NYSE").getTimezone());
        assertThat (marketProviderDate.getDayOfWeek())
                .isEqualByComparingTo(DayOfWeek.FRIDAY);
        log.info("Computed Date {}", marketProviderDate);
    }


}
