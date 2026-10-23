package com.goldys.platform.query;

import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes compiled parameterised SQL on a read-only transaction with a statement timeout. Values
 * are bind parameters and the transaction is read-only, so the query path cannot mutate data.
 * Returns positional rows in the compiled select order.
 */
@Component
public class QueryExecutor {
  private final JdbcTemplate jdbc;

  public QueryExecutor(@Qualifier("queryJdbcTemplate") JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(
      readOnly = true,
      propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
  public List<List<Object>> execute(String sql, List<Object> params) {
    jdbc.execute("SET LOCAL ROLE goldys_ro");
    return jdbc.query(
        sql,
        params.toArray(),
        rs -> {
          java.util.List<List<Object>> out = new java.util.ArrayList<>();
          int cols = rs.getMetaData().getColumnCount();
          while (rs.next()) {
            java.util.List<Object> row = new java.util.ArrayList<>(cols);
            for (int i = 1; i <= cols; i++) {
              row.add(rs.getObject(i));
            }
            out.add(row);
          }
          return out;
        });
  }
}
