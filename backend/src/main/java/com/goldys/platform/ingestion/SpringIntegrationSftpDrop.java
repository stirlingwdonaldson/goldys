package com.goldys.platform.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;

/**
 * Real SFTP transport, activated only when {@code ctb.sftp.enabled=true}. Credentials come from
 * {@code ctb.sftp.*}; provisioning is a deploy-time concern.
 */
@Configuration
@ConditionalOnProperty(name = "ctb.sftp.enabled", havingValue = "true")
public class SpringIntegrationSftpDrop {

  @Bean
  SftpDrop sftpDrop(
      @Value("${ctb.sftp.host:}") String host,
      @Value("${ctb.sftp.port:22}") int port,
      @Value("${ctb.sftp.user:}") String user,
      @Value("${ctb.sftp.password:}") String password,
      @Value("${ctb.sftp.remote-dir:/}") String remoteDir) {
    DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory();
    factory.setHost(host);
    factory.setPort(port);
    factory.setUser(user);
    factory.setPassword(password);
    factory.setAllowUnknownKeys(true);
    return create(new SftpRemoteFileTemplate(factory), remoteDir);
  }

  /** Builds the {@link SftpDrop} from a ready template; extracted for testability. */
  static SftpDrop create(SftpRemoteFileTemplate template, String remoteDir) {
    String base = remoteDir == null || remoteDir.isBlank() ? "/" : remoteDir;
    return new SftpDrop() {
      @Override
      public List<SftpFile> list() {
        return template.execute(
            session -> {
              List<SftpFile> out = new ArrayList<>();
              for (var entry : session.list(base)) {
                if (!entry.getAttributes().isDirectory()) {
                  out.add(new SftpFile(path(base, entry.getFilename()), entry.getFilename()));
                }
              }
              return out;
            });
      }

      @Override
      public byte[] download(String path) {
        return template.execute(
            session -> {
              try (InputStream in = session.readRaw(path)) {
                return in.readAllBytes();
              }
            });
      }

      @Override
      public void markProcessed(String path) {
        int slash = path.lastIndexOf('/');
        String dir = slash <= 0 ? base : path.substring(0, slash);
        String filename = path.substring(slash + 1);
        String processedDir = dir + "/processed";
        template.execute(
            session -> {
              try {
                session.mkdir(processedDir);
              } catch (IOException e) {
                // OpenSSH returns a generic "Failure" (not a distinct "already exists") when the
                // directory is already there; the rename below is the real operation and fails
                // loudly if the directory truly is absent.
              }
              session.rename(path, processedDir + "/" + filename);
              return null;
            });
      }
    };
  }

  /** Joins an SFTP directory and filename without producing a double slash. */
  private static String path(String base, String filename) {
    if ("/".equals(base)) {
      return "/" + filename;
    }
    return base.endsWith("/") ? base + filename : base + "/" + filename;
  }
}
