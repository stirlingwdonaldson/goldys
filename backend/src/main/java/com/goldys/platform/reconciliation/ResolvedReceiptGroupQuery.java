package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.*;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResolvedReceiptGroupQuery implements ReceiptGroupQuery {
  private final JdbcTemplate jdbc;

  public ResolvedReceiptGroupQuery(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ReceiptGroup group(String number, LocalDate from, LocalDate to) {
    List<ReceiptGroup.Transaction> transactions =
        jdbc.query(
            """
   select site_number,transaction_id,linked_transaction_id,source_local_time,net,tax,tip,total
   from resolved_sale_transaction where receipt_number=?
   order by site_number,transaction_id,source_local_time limit 1001
   """,
            (rs, i) ->
                new ReceiptGroup.Transaction(
                    rs.getLong("site_number"),
                    rs.getString("transaction_id"),
                    rs.getString("linked_transaction_id"),
                    rs.getObject("source_local_time", java.time.LocalDateTime.class),
                    rs.getBigDecimal("net"),
                    rs.getBigDecimal("tax"),
                    rs.getBigDecimal("tip"),
                    rs.getBigDecimal("total")),
            number);
    Long count =
        jdbc.queryForObject(
            """
   select count(*) from resolved_sale_line s where trading_date between ? and ?
   and (sale_number=? or (s.source_system,s.site_number,s.trading_date,s.receipt_line_id) in
    (select source_system,site_number,trading_date,receipt_line_id from resolved_receipt_mapping_claim where receipt_number=?))
   """,
            Long.class,
            from,
            to,
            number,
            number);
    Boolean conflict =
        jdbc.queryForObject(
            """
   select exists(select 1 from resolved_sale_line s join resolved_receipt_mapping_claim m
    on m.source_system=s.source_system and m.site_number=s.site_number and m.trading_date=s.trading_date and m.receipt_line_id=s.receipt_line_id
    where s.trading_date between ? and ? and m.receipt_number=? and s.receipt_association='MAPPING_CONFLICT')
   """,
            Boolean.class,
            from,
            to,
            number);
    Boolean transactionConflict =
        jdbc.queryForObject(
            """
      with facts as materialized (select * from resolved_sale_transaction)
      select exists(
        select 1 from facts t where t.receipt_number=?
        and (select count(*) from facts other
          where other.source_system=t.source_system and other.site_number=t.site_number
          and other.transaction_id=t.transaction_id)>1)
      """,
            Boolean.class,
            number);
    long ids =
        transactions.stream().map(t -> t.siteNumber() + ":" + t.transactionId()).distinct().count();
    String association =
        association(
            Boolean.TRUE.equals(conflict),
            Boolean.TRUE.equals(transactionConflict),
            transactions.size(),
            ids);
    return new ReceiptGroup(
        number,
        association,
        "SOURCE_COVERAGE_UNVERIFIED",
        count == null ? 0 : count,
        transactions.stream().limit(1000).toList());
  }

  private static String association(
      boolean mappingConflict, boolean transactionConflict, int observations, long identities) {
    if (mappingConflict) return "MAPPING_CONFLICT";
    if (observations > 1000) return "TRANSACTION_LIMIT_EXCEEDED";
    if (transactionConflict || observations > identities) return "TRANSACTION_CONFLICT";
    if (identities > 1) return "AMBIGUOUS_TRANSACTION";
    return "TRANSACTION_IDENTITY_UNVERIFIED";
  }
}
