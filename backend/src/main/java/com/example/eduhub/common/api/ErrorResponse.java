package com.example.eduhub.common.api;

/** 오류 응답 공통 형식. code는 클라이언트가 분기할 때 쓰는 고정 문자열이다. */
public record ErrorResponse(String code, String message) {}
