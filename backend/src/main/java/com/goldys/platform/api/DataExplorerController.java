package com.goldys.platform.api;

import com.goldys.platform.application.DataExplorerService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.semantic.DataPage;
import com.goldys.platform.semantic.EntityDescriptor;
import com.goldys.platform.semantic.GenericRow;
import com.goldys.platform.semantic.RawFilter;
import com.goldys.platform.semantic.RawRecordDetail;
import com.goldys.platform.semantic.RawRecordSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin delivery for the owner's data explorer. Authorization lives in {@link DataExplorerService}.
 */
@RestController
@RequestMapping("/api/data")
public class DataExplorerController {
  private final DataExplorerService service;
  private final CurrentUserService currentUser;

  public DataExplorerController(DataExplorerService service, CurrentUserService currentUser) {
    this.service = service;
    this.currentUser = currentUser;
  }

  @GetMapping("/raw")
  DataPage<RawRecordSummary> raw(
      @RequestParam(required = false) String source,
      @RequestParam(required = false) String fetcher,
      @RequestParam(required = false) String method,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      @AuthenticationPrincipal AccountUserDetails user) {
    return service.listRaw(
        currentUser.roleOf(user), new RawFilter(source, fetcher, method, from, to), page, size);
  }

  @GetMapping("/raw/{id}")
  RawRecordDetail rawDetail(
      @PathVariable UUID id, @AuthenticationPrincipal AccountUserDetails user) {
    return service.rawDetail(currentUser.roleOf(user), id);
  }

  @GetMapping("/canonical")
  List<EntityDescriptor> canonical(@AuthenticationPrincipal AccountUserDetails user) {
    return service.canonicalEntities(currentUser.roleOf(user));
  }

  @GetMapping("/canonical/{entity}")
  DataPage<GenericRow> canonicalRows(
      @PathVariable String entity,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      @AuthenticationPrincipal AccountUserDetails user) {
    return service.canonicalRows(currentUser.roleOf(user), entity, page, size);
  }

  @GetMapping("/resolved")
  List<EntityDescriptor> resolved(@AuthenticationPrincipal AccountUserDetails user) {
    return service.resolvedDomains(currentUser.roleOf(user));
  }

  @GetMapping("/resolved/{domain}")
  DataPage<GenericRow> resolvedRows(
      @PathVariable String domain,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      @AuthenticationPrincipal AccountUserDetails user) {
    return service.resolvedRows(currentUser.roleOf(user), domain, page, size);
  }
}
