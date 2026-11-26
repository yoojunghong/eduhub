package com.example.eduhub.auth.application;

import com.example.eduhub.employee.domain.Employee;

/** 로그인 성공 결과. ttlSeconds는 토큰 유효 시간(초). */
public record LoginResult(String accessToken, long ttlSeconds, Employee employee) {}
