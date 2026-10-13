package com.goldys.platform.connectors.lightspeed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goldys.platform.canonical.ProductIngest;
import com.goldys.platform.canonical.ProductInput;
import com.goldys.platform.ingestion.FetchMethod;
import com.goldys.platform.ingestion.IngestionService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LightspeedProductCatalogIngestServiceTest {

  @Mock IngestionService ingestion;
  @Mock LightspeedProductCatalogParser parser;
  @Mock ProductIngest productIngest;
  @Mock RecipeProductMatcher matcher;

  private LightspeedProductCatalogIngestService service() {
    return new LightspeedProductCatalogIngestService(ingestion, parser, productIngest, matcher);
  }

  private static byte[] csv() {
    return "ProductID,ProductName\n1,Beer\n".getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void persistsRawRecordWithFetcherIdentity() {
    when(ingestion.ingestPush(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(UUID.randomUUID());
    when(parser.parse(any())).thenReturn(List.of());

    service().ingest(csv());

    ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
    verify(ingestion)
        .ingestPush(
            eq("LIGHTSPEED"),
            eq("lightspeed-product-catalog"),
            eq(FetchMethod.FILE_EXPORT),
            eq("text/csv"),
            bytes.capture(),
            eq(StandardCharsets.UTF_8.name()),
            eq("lightspeed-product-catalog"));
    assertThat(bytes.getValue()).isEqualTo(csv());
  }

  @Test
  void recordsProductsClosesAbsentAndMatches() {
    UUID rawId = UUID.randomUUID();
    when(ingestion.ingestPush(any(), any(), any(), any(), any(), any(), any())).thenReturn(rawId);
    when(parser.parse(any()))
        .thenReturn(
            List.of(
                new LightspeedProduct(
                    1, "Beer", "Drinks", new BigDecimal("10.00"), false, "Goldy's Food")));

    service().ingest(csv());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<ProductInput>> captor = ArgumentCaptor.forClass(List.class);
    verify(productIngest).recordAll(captor.capture());
    List<ProductInput> inputs = captor.getValue();
    assertThat(inputs).hasSize(1);
    ProductInput input = inputs.get(0);
    assertThat(input.sourceSystem()).isEqualTo("LIGHTSPEED");
    assertThat(input.productId()).isEqualTo(1);
    assertThat(input.productName()).isEqualTo("Beer");
    assertThat(input.category()).isEqualTo("Drinks");
    assertThat(input.sellPriceIncTax()).isEqualByComparingTo("10.00");
    assertThat(input.isModifier()).isFalse();
    assertThat(input.reportingGroup()).isEqualTo("Goldy's Food");
    assertThat(input.rawRecordId()).isEqualTo(rawId);

    verify(productIngest).closeAbsent(Set.of(1));
    verify(matcher).match();
  }
}
