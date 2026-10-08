package com.goldys.platform.ingestion;

import java.util.List;

/**
 * Transport-agnostic view of the CTB SFTP drop (spec §10). Swappable so the transport is isolated.
 */
public interface SftpDrop {
  record SftpFile(String path, String filename) {}

  List<SftpFile> list();

  byte[] download(String path);
}
