package com.example.eduhub.course.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
public class Course {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String title;
  private String category;
  private String description;

  @Enumerated(EnumType.STRING)
  private CourseStatus status = CourseStatus.OPEN;

  @OneToMany(mappedBy = "course")
  private List<CourseSession> sessions = new ArrayList<>();

  protected Course() {}

  public Course(String title, String category, String description) {
    this.title = title;
    this.category = category;
    this.description = description;
  }

  public void update(String title, String category, String description) {
    if (status == CourseStatus.CLOSED) {
      throw new CourseClosedException(id); // 종료된 과정은 수정 불가 (FR-03)
    }
    this.title = title;
    this.category = category;
    this.description = description;
  }

  /** 과정을 종료하면 회차도 모두 종료한다. 남은 신청 취소(D-04)는 수강 신청(FR-04) 이후에 붙인다. */
  public void close() {
    this.status = CourseStatus.CLOSED;
    sessions.forEach(CourseSession::close);
  }

  public CourseSession addSession(LocalDate startDate, String place, int capacity) {
    if (status == CourseStatus.CLOSED) {
      throw new CourseClosedException(id); // 종료된 과정에는 회차를 추가할 수 없다
    }
    CourseSession session = new CourseSession(this, startDate, place, capacity);
    sessions.add(session);
    return session;
  }

  public Long getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getCategory() {
    return category;
  }

  public String getDescription() {
    return description;
  }

  public CourseStatus getStatus() {
    return status;
  }

  public List<CourseSession> getSessions() {
    return Collections.unmodifiableList(sessions);
  }
}
