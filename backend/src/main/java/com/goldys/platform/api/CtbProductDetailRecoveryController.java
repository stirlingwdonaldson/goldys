package com.goldys.platform.api;

import com.goldys.platform.application.CtbProductDetailRecoveryApplicationService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.connectors.ctb.CtbProductDetailRecovery;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/connectors/ctb/product-detail-recovery")
public class CtbProductDetailRecoveryController {
  private final CtbProductDetailRecoveryApplicationService recovery;
  private final CurrentUserService currentUser;

  public CtbProductDetailRecoveryController(
      CtbProductDetailRecoveryApplicationService recovery, CurrentUserService currentUser) {
    this.recovery = recovery;
    this.currentUser = currentUser;
  }

  @PostMapping("/preview")
  CtbProductDetailRecovery.Preview preview(
      @RequestBody CtbProductDetailRecovery.Request request,
      @AuthenticationPrincipal AccountUserDetails user) {
    return recovery.preview(currentUser.roleOf(user), request);
  }

  @PostMapping("/apply")
  CtbProductDetailRecovery.Result apply(
      @RequestBody Apply request, @AuthenticationPrincipal AccountUserDetails user) {
    return recovery.apply(
        currentUser.roleOf(user), request.record(), request.approvedPreviewSha256());
  }

  public record Apply(CtbProductDetailRecovery.Request record, String approvedPreviewSha256) {
    public Apply {
      if (record == null) throw new IllegalArgumentException("Recovery record is required");
    }
  }
}
