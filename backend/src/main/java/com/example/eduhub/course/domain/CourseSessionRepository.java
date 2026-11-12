package com.example.eduhub.course.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/** 회차 저장용. 회차 생성은 {@link Course#addSession}으로 하고, 저장만 이 저장소로 한다. */
public interface CourseSessionRepository extends JpaRepository<CourseSession, Long> {}
