/**
 * Viewer-adjustable graph settings, modelled on Obsidian's graph view: display (text fade, node
 * size, link thickness) and forces (center, repel, link). Pure helpers live here so the renderer
 * stays thin.
 */

export interface DisplaySettings {
  /** -1..1. Lower shows labels on smaller nodes / at lower zoom; Obsidian's "text fade threshold". */
  textFade: number;
  /** 0.5..3, multiplies every node size. */
  nodeSize: number;
  /** 0.25..3, multiplies every link width. */
  linkThickness: number;
}

export interface ForceSettings {
  /** 0..1, pull towards the middle (ForceAtlas2 gravity). */
  center: number;
  /** 0..20, how hard nodes push each other apart (ForceAtlas2 scaling ratio). */
  repel: number;
  /** 0..1, how much heavier links pull harder (ForceAtlas2 edge weight influence). */
  link: number;
}

export interface GraphSettings {
  display: DisplaySettings;
  forces: ForceSettings;
}

export const DEFAULT_SETTINGS: GraphSettings = {
  display: { textFade: 0, nodeSize: 1, linkThickness: 1 },
  forces: { center: 0.3, repel: 8, link: 0.5 },
};

/**
 * Node size in screen pixels at the default zoom (Sigma's `size`). Like Obsidian, connections drive
 * size so hubs read as hubs; the node's own `size` adds a little so a source outweighs a metric
 * with the same degree.
 */
export function nodeSize(degree: number, size: number, scale: number): number {
  return scale * (3.5 + 1.6 * Math.sqrt(Math.max(0, degree)) + 0.35 * size);
}

/**
 * Sigma draws a node's label only once the node renders at least this many pixels, and nodes grow
 * as you zoom, so labels fade in with zoom. The slider moves the threshold between 2px (almost
 * every label) and 14px (only big nodes, or when zoomed right in).
 */
export function labelThreshold(textFade: number): number {
  return 8 + 6 * Math.max(-1, Math.min(1, textFade));
}

/**
 * Maps the force sliders onto ForceAtlas2 settings. Strong-gravity mode with size-aware repulsion
 * settles into a round, Obsidian-like cloud without overlapping nodes; the default linear gravity
 * stretches tree-like graphs (the platform map) into a long thin column.
 */
export function fa2Settings(forces: ForceSettings) {
  return {
    gravity: 0.05 + forces.center,
    scalingRatio: 2 + forces.repel * 2,
    edgeWeightInfluence: forces.link * 2,
    strongGravityMode: true,
    adjustSizes: true,
    linLogMode: false,
    barnesHutOptimize: false,
    slowDown: 2,
  };
}

/** Reads saved settings, filling anything missing or malformed from the defaults. */
export function readSettings(key: string): GraphSettings {
  try {
    const raw = window.localStorage.getItem(`graph-settings:${key}`);
    if (!raw) return DEFAULT_SETTINGS;
    const parsed = JSON.parse(raw) as Partial<GraphSettings>;
    return {
      display: pickNumbers(DEFAULT_SETTINGS.display, parsed.display),
      forces: pickNumbers(DEFAULT_SETTINGS.forces, parsed.forces),
    };
  } catch {
    return DEFAULT_SETTINGS;
  }
}

export function writeSettings(key: string, settings: GraphSettings): void {
  try {
    window.localStorage.setItem(`graph-settings:${key}`, JSON.stringify(settings));
  } catch {
    // Storage unavailable: settings last for this visit only.
  }
}

function pickNumbers<T extends object>(defaults: T, saved: unknown): T {
  const result = { ...defaults };
  if (saved && typeof saved === "object") {
    for (const key of Object.keys(defaults) as (keyof T)[]) {
      const value = (saved as Record<string, unknown>)[key as string];
      if (typeof value === "number" && Number.isFinite(value)) result[key] = value as T[keyof T];
    }
  }
  return result;
}
