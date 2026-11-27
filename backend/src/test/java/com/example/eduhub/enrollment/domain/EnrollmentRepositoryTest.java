package com.example.eduhub.enrollment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.eduhub.course.domain.Course;
import com.example.eduhub.course.domain.CourseRepository;
import com.example.eduhub.course.domain.CourseSession;
import com.example.eduhub.course.domain.CourseSessionRepository;
import com.example.eduhub.employee.domain.Employee;
import com.example.eduhub.employee.domain.EmployeeRole;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.EMBEDDED)
@ActiveProfiles("test")
class EnrollmentRepositoryTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now();

  @Autowired private EnrollmentRepository enrollmentRepository;
  @Autowired private CourseRepository courseRepository;
  @Autowired private CourseSessionRepository sessionRepository;
  @Autowired private TestEntityManager em;

  private Course newCourse() {
    return courseRepository.save(new Course("Spring Boot 입문", "BACKEND", "기초"));
  }

  private CourseSession newSession(Course course) {
    CourseSession session = course.addSession(LocalDate.now().plusMonths(1), "A동", 20);
    return sessionRepository.saveAndFlush(session);
  }

  @Test
  @DisplayName("확정 신청 수를 회차·상태별로 센다")
  void countBySessionIdAndStatus_countsOnlyGivenStatus() {
    CourseSession session = newSession(newCourse());
    enrollmentRepository.save(Enrollment.confirm(session, 1L, NOW));
    enrollmentRepository.save(Enrollment.confirm(session, 2L, NOW));
    enrollmentRepository.saveAndFlush(Enrollment.waiting(session, 3L, NOW));

    assertThat(
            enrollmentRepository.countBySessionIdAndStatus(
                session.getId(), EnrollmentStatus.CONFIRMED))
        .isEqualTo(2);
    assertThat(
            enrollmentRepository.countBySessionIdAndStatus(
                session.getId(), EnrollmentStatus.WAITING))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("같은 과정에 진행 중 신청이 있으면 존재한다고 답한다")
  void existsActiveInCourse_trueForActive() {
    Course course = newCourse();
    CourseSession session = newSession(course);
    enrollmentRepository.saveAndFlush(Enrollment.waiting(session, 1L, NOW));

    assertThat(enrollmentRepository.existsActiveInCourse(course.getId(), 1L)).isTrue();
    assertThat(enrollmentRepository.existsActiveInCourse(course.getId(), 2L)).isFalse();
  }

  @Test
  @DisplayName("회차별 확정 수를 한 번에 집계한다")
  void countConfirmedBySessionIds_groupsBySession() {
    Course course = newCourse();
    CourseSession s1 = newSession(course);
    CourseSession s2 = newSession(course);
    enrollmentRepository.save(Enrollment.confirm(s1, 1L, NOW));
    enrollmentRepository.save(Enrollment.confirm(s1, 2L, NOW));
    enrollmentRepository.saveAndFlush(Enrollment.waiting(s1, 3L, NOW));

    List<SessionCount> counts =
        enrollmentRepository.countConfirmedBySessionIds(List.of(s1.getId(), s2.getId()));

    assertThat(counts).containsExactly(new SessionCount(s1.getId(), 2));
  }

  // clear 후 앞서 저장한 엔티티는 detached 상태이므로 id만 쓸 것
  private void setStatus(Enrollment e, String status) {
    em.getEntityManager()
        .createNativeQuery("update enrollment set status = :s where id = :id")
        .setParameter("s", status)
        .setParameter("id", e.getId())
        .executeUpdate();
    em.clear();
  }

  @Test
  @DisplayName("대기 순번은 같은 회차 대기자 중 자기 이하 id 수이며 다른 회차·상태는 세지 않는다")
  void countWaitingAhead_countsOnlyWaitingInSameSession() {
    Course course = newCourse();
    CourseSession s1 = newSession(course);
    CourseSession s2 = newSession(course);
    enrollmentRepository.save(Enrollment.waiting(s2, 4L, NOW));
    enrollmentRepository.save(Enrollment.confirm(s1, 10L, NOW));
    Enrollment canceled = enrollmentRepository.save(Enrollment.waiting(s1, 11L, NOW));
    Enrollment w1 = enrollmentRepository.save(Enrollment.waiting(s1, 1L, NOW));
    Enrollment w2 = enrollmentRepository.save(Enrollment.waiting(s1, 2L, NOW));
    Enrollment w3 = enrollmentRepository.saveAndFlush(Enrollment.waiting(s1, 3L, NOW));
    setStatus(canceled, "CANCELED");

    Long sid = s1.getId();
    assertThat(enrollmentRepository.countWaitingAhead(sid, w1.getId())).isEqualTo(1);
    assertThat(enrollmentRepository.countWaitingAhead(sid, w2.getId())).isEqualTo(2);
    assertThat(enrollmentRepository.countWaitingAhead(sid, w3.getId())).isEqualTo(3);
  }

  @Test
  @DisplayName("승급 대상은 가장 작은 id의 대기자다")
  void findFirstWaiting_returnsSmallestId() {
    Course course = newCourse();
    CourseSession session = newSession(course);
    CourseSession other = newSession(course);
    enrollmentRepository.save(Enrollment.waiting(other, 5L, NOW));
    Enrollment canceled = enrollmentRepository.save(Enrollment.waiting(session, 9L, NOW));
    Enrollment first = enrollmentRepository.save(Enrollment.waiting(session, 1L, NOW));
    enrollmentRepository.saveAndFlush(Enrollment.waiting(session, 2L, NOW));
    setStatus(canceled, "CANCELED");

    assertThat(enrollmentRepository.findFirstWaiting(session.getId()))
        .get()
        .extracting(Enrollment::getId)
        .isEqualTo(first.getId());
  }

  @Test
  @DisplayName("대기자가 없으면 승급 대상도 없다")
  void findFirstWaiting_emptyWhenNoWaiting() {
    CourseSession session = newSession(newCourse());
    enrollmentRepository.saveAndFlush(Enrollment.confirm(session, 1L, NOW));

    assertThat(enrollmentRepository.findFirstWaiting(session.getId())).isEmpty();
  }

  @Test
  @DisplayName("본인 신청이면 회차 id를, 남의 신청이나 없는 신청이면 빈 값을 돌려준다")
  void findSessionIdByIdAndEmployeeId_ownerOnly() {
    CourseSession session = newSession(newCourse());
    Enrollment e = enrollmentRepository.saveAndFlush(Enrollment.confirm(session, 1L, NOW));

    assertThat(enrollmentRepository.findSessionIdByIdAndEmployeeId(e.getId(), 1L))
        .contains(session.getId());
    assertThat(enrollmentRepository.findSessionIdByIdAndEmployeeId(e.getId(), 2L)).isEmpty();
    assertThat(enrollmentRepository.findSessionIdByIdAndEmployeeId(-1L, 1L)).isEmpty();
  }

  private Employee newEmployee(String no, String name) {
    return em.persist(new Employee(no, name, "개발1팀", "hash", EmployeeRole.EMPLOYEE));
  }

  private Statistics statistics() {
    return em.getEntityManager()
        .getEntityManagerFactory()
        .unwrap(SessionFactory.class)
        .getStatistics();
  }

  @Test
  @DisplayName("내 신청 내역은 내 것만 최근 신청순으로, 회차·과정 정보와 함께 나온다")
  void findMyEnrollments_onlyMineNewestFirst() {
    Course c1 = newCourse();
    Course c2 = courseRepository.save(new Course("JPA 실전", "BACKEND", "심화"));
    CourseSession s1 = newSession(c1);
    CourseSession s2 = newSession(c2);
    Enrollment a1 = enrollmentRepository.save(Enrollment.confirm(s1, 1L, NOW));
    enrollmentRepository.save(Enrollment.confirm(s1, 2L, NOW));
    Enrollment a2 = enrollmentRepository.saveAndFlush(Enrollment.confirm(s2, 1L, NOW));

    List<MyEnrollmentRow> rows = enrollmentRepository.findMyEnrollments(1L, null);

    assertThat(rows)
        .extracting(MyEnrollmentRow::enrollmentId)
        .containsExactly(a2.getId(), a1.getId());
    MyEnrollmentRow first = rows.get(0);
    assertThat(first.sessionId()).isEqualTo(s2.getId());
    assertThat(first.courseId()).isEqualTo(c2.getId());
    assertThat(first.courseTitle()).isEqualTo("JPA 실전");
    assertThat(first.startDate()).isEqualTo(s2.getStartDate());
    assertThat(first.place()).isEqualTo("A동");
    assertThat(first.status()).isEqualTo(EnrollmentStatus.CONFIRMED);
    assertThat(first.waitingNumber()).isNull();
    assertThat(first.enrolledAt()).isNotNull();
  }

  @Test
  @DisplayName("상태로 거르면 그 상태만, 대기 순번은 앞사람이 취소되면 당겨진다")
  void findMyEnrollments_statusFilterAndWaitingNumber() {
    Course c1 = newCourse();
    Course c2 = courseRepository.save(new Course("JPA 실전", "BACKEND", "심화"));
    CourseSession s1 = newSession(c1);
    CourseSession s2 = newSession(c2);
    Enrollment ahead = enrollmentRepository.save(Enrollment.waiting(s1, 10L, NOW));
    Enrollment mine = enrollmentRepository.save(Enrollment.waiting(s1, 1L, NOW));
    enrollmentRepository.saveAndFlush(Enrollment.confirm(s2, 1L, NOW));

    List<MyEnrollmentRow> waiting =
        enrollmentRepository.findMyEnrollments(1L, EnrollmentStatus.WAITING);
    assertThat(waiting).hasSize(1);
    assertThat(waiting.get(0).enrollmentId()).isEqualTo(mine.getId());
    assertThat(waiting.get(0).waitingNumber()).isEqualTo(2L);

    setStatus(ahead, "CANCELED");
    assertThat(
            enrollmentRepository
                .findMyEnrollments(1L, EnrollmentStatus.WAITING)
                .get(0)
                .waitingNumber())
        .isEqualTo(1L);
    assertThat(enrollmentRepository.findMyEnrollments(1L, EnrollmentStatus.COMPLETED)).isEmpty();
    assertThat(enrollmentRepository.findMyEnrollments(1L, null)).hasSize(2);
  }

  @Test
  @DisplayName("내 신청 내역은 신청이 여러 건이어도 SQL 1번이다")
  void findMyEnrollments_singleStatement() {
    for (int i = 0; i < 5; i++) {
      Course course = courseRepository.save(new Course("과정" + i, "BACKEND", "d"));
      CourseSession s = newSession(course);
      if (i % 2 == 0) {
        enrollmentRepository.save(Enrollment.waiting(s, 1L, NOW));
      } else {
        enrollmentRepository.save(Enrollment.confirm(s, 1L, NOW));
      }
    }
    em.flush();
    em.clear();
    Statistics statistics = statistics();
    statistics.clear();

    List<MyEnrollmentRow> rows = enrollmentRepository.findMyEnrollments(1L, null);

    assertThat(rows).hasSize(5);
    assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("명단은 신청 순으로 사원 정보와 함께 나오고 상태로 거르며, 대기 순번이 붙는다")
  void findRoster_filtersStatusesWithEmployeeAndWaitingNumber() {
    CourseSession session = newSession(newCourse());
    CourseSession other = newSession(newCourse());
    Employee e1 = newEmployee("E1001", "김사원");
    Employee e2 = newEmployee("E1002", "이사원");
    Employee e3 = newEmployee("E1003", "박사원");
    Employee e4 = newEmployee("E1004", "최사원");
    Enrollment c1 = enrollmentRepository.save(Enrollment.confirm(session, e1.getId(), NOW));
    Enrollment canceled = enrollmentRepository.save(Enrollment.confirm(session, e4.getId(), NOW));
    Enrollment w1 = enrollmentRepository.save(Enrollment.waiting(session, e2.getId(), NOW));
    Enrollment w2 = enrollmentRepository.save(Enrollment.waiting(session, e3.getId(), NOW));
    enrollmentRepository.saveAndFlush(Enrollment.confirm(other, e1.getId(), NOW));
    setStatus(canceled, "CANCELED");

    List<RosterRow> confirmed =
        enrollmentRepository.findRoster(
            session.getId(),
            List.of(
                EnrollmentStatus.CONFIRMED,
                EnrollmentStatus.COMPLETED,
                EnrollmentStatus.INCOMPLETE));
    List<RosterRow> waiting =
        enrollmentRepository.findRoster(session.getId(), List.of(EnrollmentStatus.WAITING));

    assertThat(confirmed).hasSize(1);
    assertThat(confirmed.get(0).enrollmentId()).isEqualTo(c1.getId());
    assertThat(confirmed.get(0).employeeNo()).isEqualTo("E1001");
    assertThat(confirmed.get(0).name()).isEqualTo("김사원");
    assertThat(confirmed.get(0).department()).isEqualTo("개발1팀");
    assertThat(confirmed.get(0).waitingNumber()).isNull();
    assertThat(waiting)
        .extracting(RosterRow::enrollmentId, RosterRow::waitingNumber)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(w1.getId(), 1L),
            org.assertj.core.groups.Tuple.tuple(w2.getId(), 2L));
  }

  @Test
  @DisplayName("사원 행이 없는 신청도 명단에 나온다 (사번·이름 null)")
  void findRoster_includesEnrollmentWithoutEmployeeRow() {
    CourseSession session = newSession(newCourse());
    enrollmentRepository.saveAndFlush(Enrollment.confirm(session, 999_999L, NOW));

    List<RosterRow> rows =
        enrollmentRepository.findRoster(session.getId(), List.of(EnrollmentStatus.CONFIRMED));

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).employeeId()).isEqualTo(999_999L);
    assertThat(rows.get(0).employeeNo()).isNull();
    assertThat(rows.get(0).name()).isNull();
    assertThat(rows.get(0).department()).isNull();
  }

  @Test
  @DisplayName("명단 조회도 SQL 1번이다")
  void findRoster_singleStatement() {
    CourseSession session = newSession(newCourse());
    for (long i = 1; i <= 4; i++) {
      Employee emp = newEmployee("E" + i, "사원" + i);
      enrollmentRepository.save(
          i % 2 == 0
              ? Enrollment.waiting(session, emp.getId(), NOW)
              : Enrollment.confirm(session, emp.getId(), NOW));
    }
    em.flush();
    em.clear();
    Statistics statistics = statistics();
    statistics.clear();

    List<RosterRow> rows =
        enrollmentRepository.findRoster(
            session.getId(), List.of(EnrollmentStatus.CONFIRMED, EnrollmentStatus.WAITING));

    assertThat(rows).hasSize(4);
    assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("회차의 해당 사원 신청만 한 번에 조회한다")
  void findBySessionIdAndEmployeeIdIn_onlyGivenSessionAndEmployees() {
    Course course = newCourse();
    CourseSession s1 = newSession(course);
    CourseSession s2 = newSession(courseRepository.save(new Course("JPA 실전", "BACKEND", "d")));
    Enrollment a = enrollmentRepository.save(Enrollment.confirm(s1, 1L, NOW));
    Enrollment b = enrollmentRepository.save(Enrollment.waiting(s1, 2L, NOW));
    enrollmentRepository.save(Enrollment.confirm(s1, 3L, NOW));
    enrollmentRepository.saveAndFlush(Enrollment.confirm(s2, 1L, NOW));

    List<Enrollment> found =
        enrollmentRepository.findBySessionIdAndEmployeeIdIn(s1.getId(), List.of(1L, 2L, 99L));

    assertThat(found).extracting(Enrollment::getId).containsExactlyInAnyOrder(a.getId(), b.getId());
  }

  @Test
  @DisplayName("같은 과정에 진행 중 신청 2건을 저장하면 유일 인덱스 위반이다")
  void save_twoActiveInSameCourse_violatesUniqueIndex() {
    Course course = newCourse();
    CourseSession s1 = newSession(course);
    CourseSession s2 = newSession(course);
    enrollmentRepository.saveAndFlush(Enrollment.confirm(s1, 1L, NOW));

    assertThatThrownBy(() -> enrollmentRepository.saveAndFlush(Enrollment.waiting(s2, 1L, NOW)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("취소된 신청은 유일 인덱스 대상이 아니어서 다시 신청할 수 있다")
  void save_afterCanceled_succeeds() {
    Course course = newCourse();
    CourseSession session = newSession(course);
    Enrollment first = enrollmentRepository.saveAndFlush(Enrollment.confirm(session, 1L, NOW));
    em.getEntityManager()
        .createNativeQuery("update enrollment set status = 'CANCELED' where id = :id")
        .setParameter("id", first.getId())
        .executeUpdate();
    em.clear();

    Enrollment again = enrollmentRepository.saveAndFlush(Enrollment.confirm(session, 1L, NOW));

    assertThat(again.getId()).isNotNull();
    assertThat(enrollmentRepository.existsActiveInCourse(course.getId(), 1L)).isTrue();
  }
}
