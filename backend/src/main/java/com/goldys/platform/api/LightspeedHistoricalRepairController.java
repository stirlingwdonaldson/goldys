package com.goldys.platform.api;

import com.goldys.platform.application.LightspeedHistoricalRepairApplicationService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.connectors.lightspeed.LightspeedHistoricalRepair;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Operator-only preview/apply; ordinary scheduled webhooks retain their existing behaviour. */
@RestController
@RequestMapping("/api/connectors/lightspeed/historical-repair")
public class LightspeedHistoricalRepairController {
  private final LightspeedHistoricalRepairApplicationService repair;
  private final CurrentUserService currentUser;

  public LightspeedHistoricalRepairController(
      LightspeedHistoricalRepairApplicationService repair, CurrentUserService currentUser) {
    this.repair = repair;
    this.currentUser = currentUser;
  }

  @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  LightspeedHistoricalRepair.Preview preview(
      @RequestParam("file") MultipartFile file, @AuthenticationPrincipal AccountUserDetails user)
      throws IOException {
    return repair.preview(currentUser.roleOf(user), read(file));
  }

  @PostMapping(value = "/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  LightspeedHistoricalRepair.Result apply(
      @RequestParam("file") MultipartFile file,
      @RequestParam("approvedPreviewSha256") String approvedSha256,
      @AuthenticationPrincipal AccountUserDetails user)
      throws IOException {
    return repair.apply(currentUser.roleOf(user), read(file), approvedSha256);
  }

  private static byte[] read(MultipartFile file) throws IOException {
    if (file.getSize() > 1_501_148)
      throw new IllegalArgumentException("Export exceeds the bounded repair size");
    return file.getBytes();
  }
}
