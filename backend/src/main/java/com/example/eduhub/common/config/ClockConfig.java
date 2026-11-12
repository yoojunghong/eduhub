package com.example.eduhub.common.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 현재 시각은 Clock으로 주입해 테스트에서 고정할 수 있게 한다. 날짜 판단(D-01)은 JVM 시간대가 아니라 KST 기준이다. */
@Configuration
public class ClockConfig {

  @Bean
  public Clock clock() {
    return Clock.system(ZoneId.of("Asia/Seoul"));
  }
}
