package com.goldys.platform.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Retired source: old webhook deliveries must never create new Deputy evidence. */
@RestController
@RequestMapping("/api/ingest")
public class DeputyIngestController {
  @PostMapping("/deputy")
  ResponseEntity<Void> deputy() {
    return ResponseEntity.status(HttpStatus.GONE).build();
  }
}
