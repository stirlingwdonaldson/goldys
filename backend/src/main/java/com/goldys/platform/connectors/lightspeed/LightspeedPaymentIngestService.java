package com.goldys.platform.connectors.lightspeed;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.canonical.CanonicalPaymentIngest;
import com.goldys.platform.canonical.PaymentInput;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import com.goldys.platform.ingestion.IngestionStageKind;
import com.goldys.platform.ingestion.IngestionStageOutcome;
import com.goldys.platform.ingestion.port.ConnectorFetchException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Lightspeed "all-payments" webhook pipeline: persist the Looker envelope byte-faithfully, then
 * parse the embedded CSV and record one canonical payment per tender row.
 */
@Service
public class LightspeedPaymentIngestService {
  private static final ObjectMapper MAPPER =
      new ObjectMapper(
          JsonFactory.builder()
              .streamReadConstraints(
                  StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build())
              .build());

  private final IngestionService ingestion;
  private final CanonicalPaymentIngest canonical;
  private final LightspeedPaymentCsvParser parser;

  public LightspeedPaymentIngestService(
      IngestionService ingestion,
      CanonicalPaymentIngest canonical,
      LightspeedPaymentCsvParser parser) {
    this.ingestion = ingestion;
    this.canonical = canonical;
    this.parser = parser;
  }

  public void ingest(byte[] body) {
    UUID rawId =
        ingestion.ingestPush(
            "LIGHTSPEED",
            "lightspeed-payments",
            FetchMethod.FILE_EXPORT,
            "application/json",
            body,
            StandardCharsets.UTF_8.name(),
            "lightspeed-payments");

    List<LightspeedPayment> payments;
    try {
      payments = parser.parse(extractCsv(body).getBytes(StandardCharsets.UTF_8));
    } catch (RuntimeException e) {
      ingestion.recordStage(rawId, IngestionStageKind.PARSED, IngestionStageOutcome.FAILED);
      throw e;
    }
    ingestion.recordStage(
        rawId,
        IngestionStageKind.PARSED,
        payments.isEmpty() ? IngestionStageOutcome.EMPTY : IngestionStageOutcome.SUCCESS);

    try {
      for (LightspeedPayment p : payments) {
        canonical.record(
            new PaymentInput(
                "LIGHTSPEED",
                p.createdDate(),
                p.saleNumber(),
                p.paymentTypeCode(),
                p.paymentTypeName(),
                p.paymentSourceType(),
                p.lspayPaymentMode(),
                p.clearingAccount(),
                nz(p.amount()),
                nz(p.tip()),
                nz(p.tendered()),
                nz(p.surcharge()),
                nz(p.paymentCount()),
                nz(p.tipCount()),
                p.reconciled(),
                p.registerCode(),
                p.registerName(),
                p.staffName(),
                p.staffCode(),
                p.siteId(),
                p.customerName(),
                rawId));
      }
    } catch (RuntimeException e) {
      ingestion.recordStage(rawId, IngestionStageKind.CANONICALIZED, IngestionStageOutcome.FAILED);
      throw e;
    }
    ingestion.recordStage(
        rawId,
        IngestionStageKind.CANONICALIZED,
        payments.isEmpty() ? IngestionStageOutcome.EMPTY : IngestionStageOutcome.SUCCESS);
  }

  private static String extractCsv(byte[] body) {
    try {
      JsonNode root = MAPPER.readTree(body);
      String data = root.path("attachment").path("data").asText(null);
      if (data == null) {
        throw new ConnectorFetchException(
            "CONNECTOR_SCHEMA_MISMATCH", "No attachment.data in Lightspeed webhook payload");
      }
      return data;
    } catch (IOException e) {
      throw new ConnectorFetchException("CONNECTOR_SCHEMA_MISMATCH", "Non-JSON webhook payload", e);
    }
  }

  private static BigDecimal nz(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }

  private static int nz(Integer v) {
    return v == null ? 0 : v;
  }
}
