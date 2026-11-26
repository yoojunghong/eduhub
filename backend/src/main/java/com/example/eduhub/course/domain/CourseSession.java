package com.example.eduhub.course.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDate;

/** 과정의 회차. 생성은 {@link Course#addSession}으로만 한다. */
@Entity
public class CourseSession {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "course_id")
  private Course course;

  private LocalDate startDate;
  private String place;
  private int capacity;

  @Enumerated(EnumType.STRING)
  private SessionStatus status = SessionStatus.OPEN;

  protected CourseSession() {}

  CourseSession(Course course, LocalDate startDate, String place, int capacity) {
    this.course = course;
    this.startDate = startDate;
    this.place = place;
    this.capacity = capacity;
  }

  public void close() {
    this.status = SessionStatus.CLOSED;
  }

  /** 대기 상한: 정원의 50% 내림 (D-06). */
  public int waitlistLimit() {
    return capacity / 2;
  }

  public Long getId() {
    return id;
  }

  public Course getCourse() {
    return course;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public String getPlace() {
    return place;
  }

  public int getCapacity() {
    return capacity;
  }

  public SessionStatus getStatus() {
    return status;
  }
}
