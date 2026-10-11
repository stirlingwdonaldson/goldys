package com.goldys.platform.semantic;

/**
 * One undirected edge of a {@link NetworkGraphDto}.
 *
 * @param weight 0..1; drives stroke width and link strength
 * @param label tooltip text; may be null
 */
public record NetworkEdgeDto(String source, String target, double weight, String label) {}
