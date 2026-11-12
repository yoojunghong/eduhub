package com.example.eduhub.enrollment;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 동시성 테스트 전용. zonky 임베디드 DataSource는 커넥션 풀이 없어 getConnection마다 새 PostgreSQL 백엔드를 띄우므로(Windows에서 수십
 * ms) 트랜잭션 시작이 엇갈려 사실상 순차 실행된다. 미리 채운 풀로 감싸서 모든 스레드가 즉시 커넥션을 받고 진짜로 동시에 출발하게 한다. 이 설정을
 * {@code @Import}한 테스트에만 적용된다.
 */
@TestConfiguration(proxyBeanMethods = false)
class PrefilledPoolTestConfig {

  static final int POOL_SIZE = 40;

  @Bean
  static BeanPostProcessor prefilledPoolPostProcessor() {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!(bean instanceof DataSource delegate) || bean instanceof HikariDataSource) {
          return bean;
        }
        HikariDataSource pool = new HikariDataSource();
        pool.setDataSource(delegate);
        pool.setMaximumPoolSize(POOL_SIZE);
        pool.setMinimumIdle(POOL_SIZE);
        pool.setPoolName("concurrency-test-pool");
        try (var c = pool.getConnection()) {
          // 풀 초기화 트리거
        } catch (Exception e) {
          throw new IllegalStateException(e);
        }
        long deadline = System.currentTimeMillis() + 60_000;
        while (pool.getHikariPoolMXBean().getTotalConnections() < POOL_SIZE
            && System.currentTimeMillis() < deadline) {
          try {
            Thread.sleep(50);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
          }
        }
        return pool;
      }
    };
  }
}
