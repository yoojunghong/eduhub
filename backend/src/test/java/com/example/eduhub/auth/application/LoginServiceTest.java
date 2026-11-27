package com.example.eduhub.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.eduhub.employee.domain.EmployeeRepository;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class LoginServiceTest {

  @Test
  @DisplayName("사번이 없어도 PasswordEncoder.matches를 한 번 호출하고 같은 예외를 던진다")
  void 없는_사번도_해시_비교를_한다() {
    EmployeeRepository repository = mock(EmployeeRepository.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    TokenService tokenService = mock(TokenService.class);
    when(encoder.encode(any())).thenReturn("dummy-hash");
    when(repository.findByEmployeeNo("NO-SUCH")).thenReturn(Optional.empty());
    LoginService service = new LoginService(repository, encoder, tokenService);
    reset(encoder);
    when(encoder.matches(any(), any())).thenReturn(false);

    assertThatThrownBy(() -> service.login("NO-SUCH", "pw"))
        .isInstanceOf(InvalidCredentialsException.class);

    verify(encoder, times(1)).matches("pw", "dummy-hash");
  }

  @Test
  @DisplayName("BCrypt 비교는 트랜잭션 밖에서 이뤄지고 LoginService에는 @Transactional이 없다")
  void 비교는_트랜잭션_밖() throws Exception {
    EmployeeRepository repository = mock(EmployeeRepository.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    when(encoder.encode(any())).thenReturn("dummy-hash");
    when(repository.findByEmployeeNo("NO-SUCH")).thenReturn(Optional.empty());
    LoginService service = new LoginService(repository, encoder, mock(TokenService.class));
    AtomicBoolean inTx = new AtomicBoolean(true);
    when(encoder.matches(any(), any()))
        .thenAnswer(inv -> {
          inTx.set(TransactionSynchronizationManager.isActualTransactionActive());
          return false;
        });

    assertThatThrownBy(() -> service.login("NO-SUCH", "pw"))
        .isInstanceOf(InvalidCredentialsException.class);

    assertThat(inTx.get()).isFalse();
    assertThat(LoginService.class.isAnnotationPresent(Transactional.class)).isFalse();
    assertThat(
            LoginService.class
                .getMethod("login", String.class, String.class)
                .isAnnotationPresent(Transactional.class))
        .isFalse();
  }
}
