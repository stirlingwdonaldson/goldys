package com.goldys.platform.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goldys.platform.config.SecurityConfig;
import com.goldys.platform.connectors.ctb.CtInvoiceCsvIngestService;
import com.goldys.platform.connectors.lightspeed.LightspeedAllSalesIngestService;
import com.goldys.platform.connectors.lightspeed.LightspeedDeletedSaleIngestService;
import com.goldys.platform.connectors.lightspeed.LightspeedIngestService;
import com.goldys.platform.connectors.lightspeed.LightspeedPaymentIngestService;
import com.goldys.platform.connectors.lightspeed.LightspeedProductIngestService;
import com.goldys.platform.connectors.lightspeed.LightspeedSaleItemIngestService;
import com.goldys.platform.connectors.lightspeed.LightspeedZReportIngestService;
import com.goldys.platform.connectors.opentable.OpenTableCsvIngestService;
import com.goldys.platform.ingestion.IngestionService;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Guards the webhook security contract by <em>discovery</em>, not by hardcoding a second list:
 * every {@code @PostMapping} under {@code /api/ingest} must be reachable without a session or CSRF
 * token (i.e. the request reaches the controller rather than being redirected to {@code /login})
 * and must reject a wrong token with {@code 401}. A webhook added to a controller but omitted from
 * {@link SecurityConfig} fails this test, which is exactly the regression that silently locked
 * Lightspeed's Z-report out (finding F18).
 */
@WebMvcTest(
    controllers = {
      LightspeedIngestController.class,
      CtInvoiceIngestController.class,
      OpenTableCsvIngestController.class,
      DeputyIngestController.class
    })
@Import(SecurityConfig.class)
@TestPropertySource(
    properties = {
      "lightspeed.webhook-token=test-token",
      "ctb.drop-token=test-token",
      "opentable.drop-token=test-token",
      "deputy.drop-token=test-token"
    })
class IngestWebhookSecurityTest {

  @Autowired MockMvc mvc;

  @Autowired RequestMappingHandlerMapping handlerMapping;

  @MockitoBean LightspeedAllSalesIngestService allSalesIngestService;
  @MockitoBean LightspeedIngestService ingestService;
  @MockitoBean LightspeedPaymentIngestService paymentIngestService;
  @MockitoBean LightspeedDeletedSaleIngestService deletedSaleIngestService;
  @MockitoBean LightspeedSaleItemIngestService saleItemIngestService;
  @MockitoBean LightspeedProductIngestService productIngestService;
  @MockitoBean LightspeedZReportIngestService zReportIngestService;
  @MockitoBean CtInvoiceCsvIngestService csvIngestService;
  @MockitoBean OpenTableCsvIngestService openTableCsvIngestService;
  @MockitoBean IngestionService ingestionService;

  @Test
  void everyIngestPostMappingIsReachableWithoutSessionAndRejectsWrongToken() throws Exception {
    Set<String> ingestPaths = discoverIngestPostMappings();

    assertFalse(ingestPaths.isEmpty(), "expected to discover /api/ingest webhook endpoints");
    // The endpoint that prompted this test: Lightspeed's Z-report was mapped in the controller
    // but missing from the security config, so a token-only push was redirected to /login.
    assertTrue(
        ingestPaths.contains("/api/ingest/lightspeed-zreport"),
        "expected the Z-report webhook to be discovered");

    for (String path : ingestPaths) {
      // A wrong token must reach the controller and be rejected there (401) — never a 302 to
      // /login (which would mean the permitAll matcher missed it) or a 403 CSRF rejection (which
      // would mean the CSRF ignore list missed it).
      mvc.perform(
              post(path)
                  .contentType(MediaType.APPLICATION_JSON)
                  .header("X-Webhook-Token", "wrong-token")
                  .content("{}"))
          .andExpect(status().isUnauthorized());
    }
  }

  private Set<String> discoverIngestPostMappings() {
    Set<String> paths = new TreeSet<>();
    for (Map.Entry<RequestMappingInfo, HandlerMethod> entry :
        handlerMapping.getHandlerMethods().entrySet()) {
      RequestMappingInfo info = entry.getKey();
      if (!info.getMethodsCondition().getMethods().contains(RequestMethod.POST)) {
        continue;
      }
      for (String pattern : info.getPatternValues()) {
        if (pattern.startsWith("/api/ingest")) {
          paths.add(pattern);
        }
      }
    }
    return paths;
  }
}
