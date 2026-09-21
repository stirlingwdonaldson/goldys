package com.goldys.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class AccountUserDetailsServiceTest {

  private final UserAccountRepository accounts = mock(UserAccountRepository.class);
  private final AccountUserDetailsService service = new AccountUserDetailsService(accounts);

  @Test
  void loadsByNormalizedEmail() {
    when(accounts.findByEmail("a@b.com"))
        .thenReturn(
            Optional.of(
                UserAccount.create("a@b.com", "hash", "A B", "ALL", "OWNER", Instant.now())));

    AccountUserDetails user = (AccountUserDetails) service.loadUserByUsername("  A@B.com ");

    assertThat(user.email()).isEqualTo("a@b.com");
    assertThat(user.seniority()).isEqualTo("OWNER");
  }

  @Test
  void throwsForUnknownEmail() {
    when(accounts.findByEmail("x@y.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.loadUserByUsername("x@y.com"))
        .isInstanceOf(UsernameNotFoundException.class);
  }
}
