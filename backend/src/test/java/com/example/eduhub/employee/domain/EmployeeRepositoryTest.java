package com.example.eduhub.employee.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.EMBEDDED)
@ActiveProfiles("test")
class EmployeeRepositoryTest {

  @Autowired private EmployeeRepository employeeRepository;

  private Employee newEmployee(String no) {
    return new Employee(no, "김사원", "개발팀", "hash", EmployeeRole.EMPLOYEE);
  }

  @Test
  @DisplayName("사원을 저장하고 사번으로 조회한다")
  void findByEmployeeNo_returnsSavedEmployee() {
    employeeRepository.saveAndFlush(newEmployee("E1001"));

    Employee found = employeeRepository.findByEmployeeNo("E1001").orElseThrow();

    assertThat(found.getId()).isNotNull();
    assertThat(found.getName()).isEqualTo("김사원");
    assertThat(found.getDepartment()).isEqualTo("개발팀");
    assertThat(found.getRole()).isEqualTo(EmployeeRole.EMPLOYEE);
  }

  @Test
  @DisplayName("없는 사번이면 빈 결과")
  void findByEmployeeNo_unknown_returnsEmpty() {
    assertThat(employeeRepository.findByEmployeeNo("NOPE")).isEmpty();
  }

  @Test
  @DisplayName("같은 사번을 두 번 저장하면 유일 제약 위반")
  void save_duplicateEmployeeNo_throws() {
    employeeRepository.saveAndFlush(newEmployee("E1001"));

    assertThatThrownBy(() -> employeeRepository.saveAndFlush(newEmployee("E1001")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
