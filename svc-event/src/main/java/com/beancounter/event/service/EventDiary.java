package com.beancounter.event.service;

import com.beancounter.auth.client.LoginService;
import com.beancounter.common.event.CorporateEvent;
import com.beancounter.common.utils.DateUtils;
import java.time.LocalDate;
import java.util.Collection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EventDiary {

  private LoginService loginService;
  private final EventService eventService;
  private final DateUtils dateUtils = new DateUtils();
  PositionService positionService;

  public EventDiary(EventService eventService) {
    this.eventService = eventService;
  }

  @Autowired(required = false)
  public void setLoginService(LoginService loginService) {
    this.loginService = loginService;
  }

  @Autowired
  void setPositionService(PositionService positionService) {
    this.positionService = positionService;
  }

  @Scheduled(cron = "${beancounter.event.schedule:0 */30 7-18 ? * Tue-Sat}")
  void processEventsForRange() {
    if ( loginService != null ) {
      loginService.login();
    }

    LocalDate end = dateUtils.getDate();
    LocalDate start = end.minusDays(5);
    Collection<CorporateEvent> events = eventService.findInRange(start, end);
    for (CorporateEvent event : events) {
      eventService.processMessage(event);
    }

  }

}
