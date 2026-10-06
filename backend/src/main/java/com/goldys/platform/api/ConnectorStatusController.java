package com.goldys.platform.api;

import com.goldys.platform.application.ConnectorApplicationService;
import com.goldys.platform.auth.AccountUserDetails;
import com.goldys.platform.auth.CurrentUserService;
import com.goldys.platform.auth.UserRole;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Connector run status and run/upload actions. Delivery-only. */
@RestController
@RequestMapping("/api")
public class ConnectorStatusController {
  private final ConnectorApplicationService connectors;
  private final CurrentUserService currentUser;

  public ConnectorStatusController(
      ConnectorApplicationService connectors, CurrentUserService currentUser) {
    this.connectors = connectors;
    this.currentUser = currentUser;
  }

  @GetMapping("/connectors")
  List<ConnectorApplicationService.ConnectorStatus> connectors(
      @AuthenticationPrincipal AccountUserDetails user) {
    return connectors.connectors(currentUser.roleOf(user));
  }

  @PostMapping("/connectors/{source}/run")
  ConnectorApplicationService.ConnectorStatus run(
      @PathVariable String source, @AuthenticationPrincipal AccountUserDetails user) {
    return connectors.run(currentUser.roleOf(user), source);
  }

  /** Uploads a manually-exported GuestCenter reservations CSV for the OpenTable source. */
  @PostMapping(
      value = "/connectors/opentable/upload",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<Void> uploadOpenTableCsv(
      @RequestParam("file") MultipartFile file, @AuthenticationPrincipal AccountUserDetails user) {
    UserRole role = currentUser.roleOf(user);
    connectors.uploadOpenTableCsv(role, readBytes(file));
    return ResponseEntity.noContent().build();
  }

  private static byte[] readBytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new ConnectorFetchException(
          "CONNECTOR_FETCH_FAILED", "Could not read the uploaded file", e);
    }
  }
}
