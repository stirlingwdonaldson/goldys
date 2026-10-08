package com.goldys.platform.semantic;

import java.util.List;
import java.util.UUID;

/** Read port for the owner's data explorer: raw, canonical, and resolved layers. */
public interface DataExplorerQuery {

  /** Paged, filtered raw-record metadata (no payload bytes). */
  DataPage<RawRecordSummary> listRaw(RawFilter filter, int page, int size);

  /** One raw record's metadata plus its payload. */
  RawRecordDetail rawDetail(UUID id);

  /** The canonical entity types the explorer can browse. */
  List<EntityDescriptor> canonicalEntities();

  /** Paged generic rows for one canonical entity type, recent-first. */
  DataPage<GenericRow> canonicalRows(String entityId, int page, int size);

  /** The resolved domains the explorer can browse. */
  List<EntityDescriptor> resolvedDomains();

  /** Paged generic rows for one resolved domain, recent-first. */
  DataPage<GenericRow> resolvedRows(String domainId, int page, int size);
}
