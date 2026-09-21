package com.goldys.platform.connectors.ctb;

import com.goldys.platform.canonical.CanonicalDailySalesIngest;
import com.goldys.platform.canonical.CanonicalProductSalesIngest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the CTB connector, reading credentials from environment variables. */
@Configuration
public class CtbConfig {
  @Bean
  CtbConnector ctbConnector(
      @Value("${ctb.base-url:https://web.cookingthebooks.com.au}") String baseUrl,
      @Value("${ctb.email:}") String email,
      @Value("${ctb.password:}") String password,
      CtbRevenueParser parser,
      CanonicalDailySalesIngest canonical,
      CtbSaleItemParser saleItemParser,
      CanonicalProductSalesIngest productSales) {
    return new CtbConnector(
        new CtbClient(baseUrl), email, password, parser, canonical, saleItemParser, productSales);
  }
}
