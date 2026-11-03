package com.goldys.platform.classification;

import com.goldys.platform.auth.*;
import com.goldys.platform.canonical.ProductNameKey;
import com.goldys.platform.semantic.ProductReportingMapping;
import com.goldys.platform.semantic.ProductReportingMapping.*;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Curated reporting policy revisions, separate from source observations and model proposals. */
@Service
public class ProductReportingMappingService {
  private final JdbcTemplate jdbc;
  private final PermissionService permissions;
  private final FeatureTextExtractor extractor;

  public ProductReportingMappingService(
      JdbcTemplate jdbc, PermissionService permissions, FeatureTextExtractor extractor) {
    this.jdbc = jdbc;
    this.permissions = permissions;
    this.extractor = extractor;
  }

  @Transactional
  public void append(UserRole role, String actor, ProductReportingMapping m) {
    permissions.require(role, new ResourceKey("products.category"), PermissionAction.WRITE);
    validate(m);
    if (m.scope() == Scope.PRODUCT_NAME) {
      var candidate = extractor.candidates().get(m.entityKey());
      if (candidate == null || candidate.ambiguous())
        throw new IllegalArgumentException(
            "Name alias has missing or ambiguous identity evidence; use source-scoped review");
    }
    if (m.scope() != Scope.PRODUCT_NAME && !sourceIdentityExists(m))
      throw new IllegalArgumentException("Source identity has not been observed");
    jdbc.update(
        """
        insert into product_reporting_mapping(scope,source_system,entity_key,effective_from,
          sales_nature,stock_bucket,item_role,business_area,lifecycle,reason,actor)
        values (?,?,?,?,?,?,?,?,?,?,?)
        """,
        m.scope().name(),
        m.sourceSystem(),
        m.entityKey(),
        m.effectiveFrom(),
        m.salesNature().name(),
        m.stockBucket().name(),
        m.itemRole().name(),
        m.businessArea().name(),
        m.lifecycle().name(),
        m.reason(),
        actor);
  }

  private boolean sourceIdentityExists(ProductReportingMapping m) {
    String sql =
        switch (m.scope()) {
          case CTB_RECIPE ->
              "select exists(select 1 from canonical_recipe_definition where source_system='CTB' and recipe_id::text=? and superseded_at is null)";
          case CTB_STOCK ->
              "select exists(select 1 from canonical_recipe_definition_stocks s join canonical_recipe_definition d on d.id=s.snapshot_id where d.source_system='CTB' and s.stock_id::text=? and d.superseded_at is null)";
          case WASTE_LINE ->
              "select exists(select 1 from canonical_wastage where source_system='CTB' and source_record_ref=? and superseded_at is null)";
          case PRODUCT_NAME -> throw new IllegalStateException("Name aliases use candidate review");
        };
    return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, m.entityKey()));
  }

  static void validate(ProductReportingMapping m) {
    if (m == null
        || m.scope() == null
        || m.effectiveFrom() == null
        || m.salesNature() == null
        || m.stockBucket() == null
        || m.itemRole() == null
        || m.businessArea() == null
        || m.lifecycle() == null
        || m.sourceSystem() == null
        || m.entityKey() == null
        || m.entityKey().isBlank()
        || m.entityKey().length() > 512
        || m.reason() == null
        || m.reason().isBlank()
        || m.reason().length() > 4000)
      throw new IllegalArgumentException(
          "Complete dimensions, identity, effective date and review reason are required");
    if (m.scope() == Scope.PRODUCT_NAME) {
      if (!m.sourceSystem().equals("RESOLVED")
          || !m.entityKey().equals(ProductNameKey.normalize(m.entityKey())))
        throw new IllegalArgumentException(
            "Product-name mappings require RESOLVED and an exact normalized alias");
    } else if (!m.sourceSystem().equals("CTB")) {
      throw new IllegalArgumentException(
          "Only CTB source identities are supported for inventory mappings");
    }
    if ((m.scope() == Scope.CTB_RECIPE || m.scope() == Scope.CTB_STOCK)
        && !m.entityKey().matches("[1-9][0-9]{0,9}"))
      throw new IllegalArgumentException("A positive source ID is required");
    if (m.itemRole() == ItemRole.MERCHANDISE && m.salesNature() != SalesNature.OTHER
        || m.stockBucket() == StockBucket.MERCHANDISE && m.salesNature() != SalesNature.OTHER)
      throw new IllegalArgumentException("Merchandise must have OTHER sales nature");
  }

  /** All revisions through the upper date, newest first; effective dates govern every lookup. */
  public List<ProductReportingMapping> revisions(LocalDate to) {
    return jdbc.query(
        """
        select * from product_reporting_mapping where effective_from <= ?
        order by effective_from desc, id desc
        """,
        (rs, n) ->
            new ProductReportingMapping(
                Scope.valueOf(rs.getString("scope")),
                rs.getString("source_system"),
                rs.getString("entity_key"),
                rs.getObject("effective_from", LocalDate.class),
                SalesNature.valueOf(rs.getString("sales_nature")),
                StockBucket.valueOf(rs.getString("stock_bucket")),
                ItemRole.valueOf(rs.getString("item_role")),
                BusinessArea.valueOf(rs.getString("business_area")),
                Lifecycle.valueOf(rs.getString("lifecycle")),
                rs.getString("reason")),
        to);
  }
}
