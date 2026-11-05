package com.example.eduhub;

import org.springframework.boot.SpringApplication;

/**
 * PostgreSQL 설치 없이 로컬에서 앱을 띄운다: {@code ./gradlew bootTestRun}. 내장 PostgreSQL과 샘플 데이터를 쓰며, 종료하면 데이터는
 * 사라진다. 운영 코드(src/main)에는 포함되지 않는다.
 */
public class LocalEduhubApplication {

  public static void main(String[] args) {
    SpringApplication.from(EduhubApplication::main)
        .with(LocalDevConfiguration.class)
        .withAdditionalProfiles("local")
        .run(args);
  }
}
