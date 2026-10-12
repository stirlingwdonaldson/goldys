package com.goldys.platform.semantic;

/**
 * One node of a {@link NetworkGraphDto}.
 *
 * @param id stable and namespaced by kind, e.g. {@code metric:sales.gross}
 * @param kind {@code source}, {@code domain}, {@code metric}, {@code derived-metric}, {@code
 *     dashboard}, {@code permission} or {@code product}
 * @param tone the flow-graph status vocabulary: {@code ok}, {@code warn}, {@code fail}, {@code
 *     missing}, {@code info} or {@code neutral}
 * @param size relative size, 1..10
 * @param group colour group; may be null
 * @param href where the frontend's "Open" link goes; may be null
 */
public record NetworkNodeDto(
    String id,
    String kind,
    String label,
    String subtitle,
    String tone,
    int size,
    String group,
    String href) {}
