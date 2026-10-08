package com.goldys.platform.semantic;

import java.util.Map;

/** One entity/domain row rendered as an ordered column map, so the UI needs no entity knowledge. */
public record GenericRow(String id, Map<String, String> columns) {}
