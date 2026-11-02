package com.goldys.platform.api;

import com.goldys.platform.application.ReceiptEvidenceApplicationService;
import com.goldys.platform.auth.*;
import com.goldys.platform.connectors.lightspeed.LightspeedReceiptEvidenceIngestService.ImportResult;
import com.goldys.platform.semantic.ReceiptGroup;
import java.io.IOException;
import java.time.LocalDate;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class ReceiptEvidenceController {
  private final ReceiptEvidenceApplicationService service;
  private final CurrentUserService users;

  public ReceiptEvidenceController(
      ReceiptEvidenceApplicationService service, CurrentUserService users) {
    this.service = service;
    this.users = users;
  }

  @PostMapping(
      value = "/connectors/lightspeed-receipts/{dataset}/upload",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ImportResult upload(
      @PathVariable String dataset,
      @RequestParam MultipartFile file,
      @RequestParam long siteNumber,
      @RequestParam String siteName,
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @AuthenticationPrincipal AccountUserDetails user)
      throws IOException {
    return service.upload(
        users.roleOf(user), dataset, file.getBytes(), siteNumber, siteName, from, to);
  }

  @PostMapping(
      value = "/connectors/lightspeed-sale-items/upload",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Void> financial(
      @RequestParam MultipartFile file, @AuthenticationPrincipal AccountUserDetails user)
      throws IOException {
    service.financial(users.roleOf(user), file.getBytes());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/sale-items/receipt-group")
  public ReceiptGroup group(
      @RequestParam String saleNumber,
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @AuthenticationPrincipal AccountUserDetails user) {
    return service.group(users.roleOf(user), saleNumber, from, to);
  }
}
