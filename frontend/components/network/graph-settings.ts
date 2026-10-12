/**
 * Viewer-adjustable graph settings, modelled on Obsidian's graph view: display (text fade, node
 * size, link thickness) and forces (center, repel, link strength, link distance). Pure helpers
 * live here so the canvas stays a thin renderer.
 */

export interface DisplaySettings {
  /** -1..1. Lower shows labels at lower zoom; Obsidian's "text fade threshold". */
  textFade: number;
  /** 0.5..3, multiplies every node radius. */
  nodeSize: number;
  /** 0.25..3, multiplies every link width. */
  linkThickness: number;
}

export interface ForceSettings {
  /** 0..1, pull towards the middle. */
  center: number;
  /** 0..20, how hard nodes push each other apart. */
  repel: number;
  /** 0..1, how hard links pull their ends together. */
  link: number;
  /** 10..200, the length links settle at (graph units). */
  distance: number;
}

export interface GraphSettings {
  display: DisplaySettings;
  forces: ForceSettings;
}

export const DEFAULT_SETTINGS: GraphSettings = {
  display: { textFade: 0, nodeSize: 1, linkThickness: 1 },
  forces: { center: 0.5, repel: 10, link: 0.6, distance: 40 },
};

const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));

/**
 * Node radius in graph units. Like Obsidian, connections drive size (hubs read as hubs); the
 * node's own `size` adds a little so a source outweighs a metric with the same degree.
 */
export function nodeRadius(degree: number, size: number, nodeSize: number): number {
  return nodeSize * (1.8 + 0.9 * Math.sqrt(Math.max(0, degree)) + 0.25 * size);
}

/**
 * Label opacity at a zoom level. Labels fade in over a short zoom band whose start moves with the
 * threshold: at 0 they begin at 1.4× and are solid by 2×; -1 starts at 0.8×, +1 at 2×. The default
 * keeps labels off at a fitted zoom, where half-faded overlapping text reads as noise.
 */
export function labelAlpha(zoom: number, textFade: number): number {
  const start = 1.4 + 0.6 * textFade;
  return clamp((zoom - start) / 0.6, 0, 1);
}

/** Maps the force settings onto d3-force parameters. */
export function d3Forces(forces: ForceSettings): {
  charge: number;
  linkStrength: number;
  linkDistance: number;
  centerStrength: number;
} {
  return {
    charge: -forces.repel * 6,
    linkStrength: forces.link,
    linkDistance: forces.distance,
    centerStrength: forces.center,
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
