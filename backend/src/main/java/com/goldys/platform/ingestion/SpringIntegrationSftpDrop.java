package com.goldys.platform.ingestion;

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
 * Real SFTP transport, activated only when {@code app.scheduling.ctb-sftp.enabled=true}.
 * Credentials come from {@code ctb.sftp.*} properties; provisioning is a deploy-time concern.
 */
@Configuration
@ConditionalOnProperty(name = "app.scheduling.ctb-sftp.enabled", havingValue = "true")
public class SpringIntegrationSftpDrop {

  @Bean
  SftpDrop sftpDrop(
      @Value("${ctb.sftp.host}") String host,
      @Value("${ctb.sftp.port:22}") int port,
      @Value("${ctb.sftp.user}") String user,
      @Value("${ctb.sftp.password}") String password,
      @Value("${ctb.sftp.remote-dir:/}") String remoteDir) {
    DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory();
    factory.setHost(host);
    factory.setPort(port);
    factory.setUser(user);
    factory.setPassword(password);
    factory.setAllowUnknownKeys(true);
    SftpRemoteFileTemplate template = new SftpRemoteFileTemplate(factory);
    return new SftpDrop() {
      @Override
      public List<SftpFile> list() {
        return template.execute(
            session -> {
              List<SftpFile> out = new ArrayList<>();
              for (var entry : session.list(remoteDir)) {
                if (!entry.getAttributes().isDirectory()) {
                  out.add(new SftpFile(remoteDir + "/" + entry.getFilename(), entry.getFilename()));
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
    };
  }
}
