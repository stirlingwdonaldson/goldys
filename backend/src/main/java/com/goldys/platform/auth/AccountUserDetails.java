package com.goldys.platform.auth;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** The request principal: the account fields plus the Spring {@link UserDetails} contract. */
public record AccountUserDetails(
    UUID id,
    String email,
    String passwordHash,
    String displayName,
    String department,
    String seniority,
    boolean active,
    boolean mustChangePassword)
    implements UserDetails {

  /** Convenience for call sites that do not care about the forced-change flag. */
  public AccountUserDetails(
      UUID id,
      String email,
      String passwordHash,
      String displayName,
      String department,
      String seniority,
      boolean active) {
    this(id, email, passwordHash, displayName, department, seniority, active, false);
  }

  static AccountUserDetails of(UserAccount account) {
    return new AccountUserDetails(
        account.id(),
        account.email(),
        account.passwordHash(),
        account.displayName(),
        account.department(),
        account.seniority(),
        account.active(),
        account.mustChangePassword());
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of();
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return active;
  }
}
