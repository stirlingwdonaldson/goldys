package com.goldys.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthServiceTest {

  private final UserAccountRepository accounts = mock(UserAccountRepository.class);
  private final AuthService service = new AuthService(accounts, new BCryptPasswordEncoder());

  @Test
  void normalizesEmailAndHashesPassword() {
    when(accounts.existsByEmail("a@b.com")).thenReturn(false);
    when(accounts.save(any())).thenAnswer(inv -> inv.getArgument(0));

    StaffProfileSummary summary = service.signup("  A@B.com  ", "A B", "password123");

    assertThat(summary.department()).isEqualTo("ALL");
    assertThat(summary.seniority()).isEqualTo("STAFF");
    assertThat(summary.displayName()).isEqualTo("A B");
    verify(accounts).existsByEmail("a@b.com"); // normalized
  }

  @Test
  void rejectsInvalidEmail() {
    assertThatThrownBy(() -> service.signup("not-an-email", "A", "password123"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsShortPassword() {
    assertThatThrownBy(() -> service.signup("a@b.com", "A", "short"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsDuplicateEmailRegardlessOfCase() {
    when(accounts.existsByEmail("a@b.com")).thenReturn(true);

    assertThatThrownBy(() -> service.signup("A@B.com", "A", "password123"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a@b.com");
  }
}
