package com.example.eduhub.auth.application;

/** 로그인한 사원의 값만 담는다(엔티티를 application 밖으로 내보내지 않기 위함). */
public record LoginUser(Long id, String employeeNo, String name, String role) {}
