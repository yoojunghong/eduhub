package com.example.eduhub.auth.application;

import com.example.eduhub.employee.domain.Employee;
import com.example.eduhub.employee.domain.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginService {

  private static final Logger log = LoggerFactory.getLogger(LoginService.class);

  private final EmployeeRepository employeeRepository;
  private final PasswordEncoder passwordEncoder;
  private final TokenService tokenService;

  /** 사번이 없을 때도 BCrypt 비교를 한 번 하도록 쓰는 더미 해시. 응답 시간으로 사번 존재를 알 수 없게 한다. */
  private final String dummyHash;

  public LoginService(
      EmployeeRepository employeeRepository,
      PasswordEncoder passwordEncoder,
      TokenService tokenService) {
    this.employeeRepository = employeeRepository;
    this.passwordEncoder = passwordEncoder;
    this.tokenService = tokenService;
    this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
  }

  @Transactional(readOnly = true)
  public LoginResult login(String employeeNo, String password) {
    Employee employee = employeeRepository.findByEmployeeNo(employeeNo).orElse(null);
    String hash = employee != null ? employee.getPasswordHash() : dummyHash;
    boolean matches = passwordEncoder.matches(password, hash);
    if (employee == null || !matches) {
      // 사번만 남긴다. 비밀번호·토큰은 남기지 않는다
      log.info("로그인 실패: employeeNo={}", sanitize(employeeNo));
      throw new InvalidCredentialsException();
    }
    LoginUser user =
        new LoginUser(
            employee.getId(),
            employee.getEmployeeNo(),
            employee.getName(),
            employee.getRole().name());
    return new LoginResult(tokenService.issue(employee), tokenService.ttlSeconds(), user);
  }

  /** 로그 위조 방지: 개행·제어문자를 제거한다. */
  private static String sanitize(String value) {
    return value == null ? null : value.replaceAll("\\p{Cntrl}", "");
  }
}
