package com.goldys.platform.reconciliation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goldys.platform.semantic.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResolvedSaleItemListQuery implements SaleItemListQuery {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public ResolvedSaleItemListQuery(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public DataPage<SaleItemRow> page(
      String category, String number, LocalDate from, LocalDate to, int page, int size) {
    int p = Math.max(0, page), limit = Math.clamp(size, 1, 200);
    String where = " where trading_date between ? and ? ";
    List<Object> args = new ArrayList<>(List.of(from, to));
    if (category != null) {
      where += " and category_name=?";
      args.add(category);
    }
    if (number != null) {
      where +=
          """
   and (sale_number=? or (s.source_system,s.site_number,s.trading_date,s.receipt_line_id) in
    (select source_system,site_number,trading_date,receipt_line_id from resolved_receipt_mapping_claim where receipt_number=?))
   """;
      args.add(number);
      args.add(number);
    }
    Long count =
        jdbc.queryForObject(
            "select count(*) from resolved_sale_line s" + where, Long.class, args.toArray());
    args.add(limit);
    args.add((long) p * limit);
    var rows =
        jdbc.query(
            "select * from resolved_sale_line s"
                + where
                + " order by trading_date desc,receipt_line_id limit ? offset ?",
            (rs, i) -> {
              SaleAdjustment adjustment = null;
              try {
                if (rs.getString("adjustment") != null)
                  adjustment = mapper.readValue(rs.getString("adjustment"), SaleAdjustment.class);
              } catch (java.io.IOException e) {
                throw new IllegalStateException("Invalid resolved sale adjustment");
              }
              return new SaleItemRow(
                  rs.getObject("trading_date", LocalDate.class),
                  rs.getString("sale_number"),
                  rs.getString("receipt_line_id"),
                  rs.getString("item_name"),
                  rs.getString("product_number"),
                  rs.getString("sku"),
                  rs.getString("category_name"),
                  rs.getBigDecimal("quantity_sold"),
                  rs.getBigDecimal("amount"),
                  rs.getBigDecimal("sold_price_inc_tax"),
                  rs.getBigDecimal("total_tax"),
                  rs.getBigDecimal("cost_inc_tax"),
                  rs.getString("order_type"),
                  rs.getString("sale_type"),
                  rs.getString("staff_name"),
                  rs.getString("register_name"),
                  rs.getString("table_number"),
                  adjustment,
                  rs.getObject("site_number", Long.class),
                  rs.getString("receipt_association"));
            },
            args.toArray());
    return new DataPage<>(rows, count == null ? 0 : count, p, limit);
  }
}
