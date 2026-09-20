package com.goldys.platform.auth;

import org.springframework.stereotype.Service;

/** Maps the authenticated account to the role used for permission decisions. */
@Service
public class CurrentUserService {
  public UserRole roleOf(AccountUserDetails user) {
    return new UserRole(new DepartmentCode(user.department()), new SeniorityCode(user.seniority()));
  }
}
