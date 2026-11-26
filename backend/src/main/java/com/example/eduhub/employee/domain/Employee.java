package com.example.eduhub.employee.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.util.Objects;

/** 사원. 계정 생성 API는 없다. department는 비어 있을 수 있다. */
@Entity
public class Employee {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 20)
  private String employeeNo;

  @Column(nullable = false, length = 50)
  private String name;

  @Column(length = 50)
  private String department;

  @Column(nullable = false, length = 100)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EmployeeRole role;

  protected Employee() {}

  public Employee(
      String employeeNo, String name, String department, String passwordHash, EmployeeRole role) {
    Objects.requireNonNull(employeeNo, "employeeNo");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(passwordHash, "passwordHash");
    Objects.requireNonNull(role, "role");
    this.employeeNo = employeeNo;
    this.name = name;
    this.department = department;
    this.passwordHash = passwordHash;
    this.role = role;
  }

  public Long getId() {
    return id;
  }

  public String getEmployeeNo() {
    return employeeNo;
  }

  public String getName() {
    return name;
  }

  public String getDepartment() {
    return department;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public EmployeeRole getRole() {
    return role;
  }
}
