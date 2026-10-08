package com.goldys.platform.connectors.lightspeed;

import static org.mockito.Mockito.verify;

import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LightspeedZReportIngestServiceTest {

  @Test
  void ingestPersistsRawPayloadWithZReportFetcherIdentity() {
    IngestionService ingestion = Mockito.mock(IngestionService.class);
    LightspeedZReportIngestService service = new LightspeedZReportIngestService(ingestion);
    byte[] body = "{\"attachment\":{\"data\":\"...\"}}".getBytes(StandardCharsets.UTF_8);

    service.ingest(body);

    verify(ingestion)
        .ingestPush(
            "LIGHTSPEED",
            "lightspeed-zreport",
            FetchMethod.FILE_EXPORT,
            "application/json",
            body,
            StandardCharsets.UTF_8.name(),
            "lightspeed-zreport");
  }
}
