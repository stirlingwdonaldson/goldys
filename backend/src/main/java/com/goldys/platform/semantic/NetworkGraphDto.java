package com.goldys.platform.semantic;

import java.util.List;

/**
 * An undirected network for the frontend's force-directed graph: nodes and the edges between them.
 * Edge direction carries no meaning.
 */
public record NetworkGraphDto(List<NetworkNodeDto> nodes, List<NetworkEdgeDto> edges) {
  public NetworkGraphDto {
    nodes = List.copyOf(nodes);
    edges = List.copyOf(edges);
  }
}
