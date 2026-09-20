package com.goldys.platform.auth;

import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads an account by (normalized) email for the {@code DaoAuthenticationProvider}. */
@Service
public class AccountUserDetailsService implements UserDetailsService {
  private final UserAccountRepository accounts;

  public AccountUserDetailsService(UserAccountRepository accounts) {
    this.accounts = accounts;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    String email = username.trim().toLowerCase(Locale.ROOT);
    UserAccount account =
        accounts
            .findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("No account for " + email));
    return new AccountUserDetails(
        account.id(),
        account.email(),
        account.passwordHash(),
        account.displayName(),
        account.department(),
        account.seniority(),
        account.active());
  }
}
