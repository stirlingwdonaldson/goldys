"use client";

import dynamic from "next/dynamic";
import type { DisplaySettings, ForceSettings } from "./graph-settings";
import type { NetworkGraph, NetworkNode } from "./types";

// WebGL + window access: never render on the server.
const SigmaGraph = dynamic(() => import("./sigma-graph"), { ssr: false });

export interface NetworkCanvasProps {
  graph: NetworkGraph;
  /** Names the cached layout, e.g. "platform-map". */
  graphKey: string;
  ariaLabel: string;
  height: number;
  focusId?: string;
  onFocus?: (id: string | undefined) => void;
  /** Fill colour from the active colour group, or undefined for the default grey. */
  colourFor: (node: NetworkNode) => string | undefined;
  /** Extra nodes kept lit while a node is focused, e.g. everything downstream of it. */
  highlightIds?: ReadonlySet<string>;
  /** Nodes to emphasise when nothing is hovered or focused, e.g. search matches. */
  emphasisIds?: ReadonlySet<string>;
  display: DisplaySettings;
  forces: ForceSettings;
  /** Bumped by the "Animate" button: scatter the nodes and let them settle again. */
  animateTick?: number;
}

/**
 * An Obsidian-style graph rendered by Sigma.js (see `sigma-graph.tsx`): flat dots sized by their
 * connections, hairline links, labels that appear as you zoom, and hover that paints a node and its
 * links in the accent while everything else fades. Wheel zoom is off until the canvas is clicked so
 * the page still scrolls past it; the corner buttons zoom at any time.
 *
 * The canvas itself carries no text for assistive tech; `NetworkExplorer` always renders the same
 * nodes as a list.
 */
export function NetworkCanvas(props: NetworkCanvasProps) {
  return (
    <div role="region" aria-label={props.ariaLabel} className="absolute inset-0">
      <SigmaGraph {...props} />
    </div>
  );
}
