package com.goldys.platform.reconciliation;

import com.goldys.platform.semantic.InventoryObservation;
import com.goldys.platform.semantic.InventoryObservationQuery;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Read-only adapter over a non-aggregating, current-version SQL projection. */
@Service
public class ResolvedInventoryObservationQuery implements InventoryObservationQuery {
  private final JdbcTemplate jdbc;

  public ResolvedInventoryObservationQuery(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<InventoryObservation> observations(
      LocalDate from, LocalDate to, String kind, int offset, int limit) {
    return jdbc.query(
        """
        select * from resolved_inventory_observation
        where observation_date between ? and ? and (?='ALL' or kind=?)
        order by observation_date desc, kind, source_system, source_record_ref
        limit ? offset ?
        """,
        (rs, row) ->
            new InventoryObservation(
                rs.getString("kind"),
                rs.getString("source_system"),
                rs.getString("source_record_ref"),
                rs.getObject("logical_entity_id", UUID.class),
                rs.getObject("observation_date", LocalDate.class),
                rs.getString("product_name_key"),
                rs.getString("stock_code"),
                rs.getBigDecimal("quantity"),
                rs.getString("unit"),
                rs.getString("unit_size"),
                rs.getBigDecimal("unit_cost"),
                rs.getBigDecimal("source_value"),
                rs.getString("area_name"),
                rs.getObject("stocktake_partial", Boolean.class),
                rs.getObject("stocktake_locked", Boolean.class),
                rs.getInt("parent_source_id"),
                rs.getTimestamp("recorded_at").toInstant()),
        from,
        to,
        kind,
        kind,
        limit,
        offset);
  }
}
