package com.example.eduhub;

import org.springframework.boot.SpringApplication;

/**
 * PostgreSQL 설치 없이 로컬에서 앱을 띄운다: {@code ./gradlew bootTestRun}. 내장 PostgreSQL과 샘플 데이터를 쓰며, 종료하면 데이터는
 * 사라진다. 운영 코드(src/main)에는 포함되지 않는다.
 */
public class LocalEduhubApplication {

  // 이 파일은 src/test에 있어 운영 jar에 실리지 않는다. 아래 기본 비밀키는 로컬 전용이다.
  private static final String LOCAL_JWT_SECRET = "local-only-eduhub-jwt-secret-do-not-use-in-prod";

  public static void main(String[] args) {
    if (isBlank(System.getenv("EDUHUB_JWT_SECRET"))
        && isBlank(System.getProperty("eduhub.jwt.secret"))) {
      System.setProperty("eduhub.jwt.secret", LOCAL_JWT_SECRET);
    }
    SpringApplication.from(EduhubApplication::main)
        .with(LocalDevConfiguration.class)
        .withAdditionalProfiles("local")
        .run(args);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
