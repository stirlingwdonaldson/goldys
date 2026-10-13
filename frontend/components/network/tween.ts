/**
 * Eases Sigma node/edge colours and sizes between highlight states. Sigma draws with WebGL, so
 * there is no CSS transition to lean on: instead the reducers hand each element's *target* look
 * to {@link VisualTween.resolve}, which returns the look for this frame and remembers the
 * element's motion. While anything is still moving, the caller keeps calling `sigma.refresh()`
 * each animation frame (see {@link VisualTween.activeUntil}).
 *
 * A new target starts from wherever the element currently is, not from its old target, so
 * sweeping the pointer across nodes never makes a half-faded node jump.
 */

export interface Look {
  /** Any colour {@link parseColour} understands: `rgb()`, `rgba()` or `#rrggbb`. */
  color: string;
  size: number;
}

type Rgba = [number, number, number, number];

interface Motion {
  from: Rgba;
  to: Rgba;
  fromSize: number;
  toSize: number;
  start: number;
}

/** Parses `rgb()`, `rgba()` and `#rrggbb`; anything else yields null (and is not animated). */
export function parseColour(colour: string): Rgba | null {
  const rgb = /^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*(?:,\s*([\d.]+)\s*)?\)$/.exec(colour.trim());
  if (rgb) return [Number(rgb[1]), Number(rgb[2]), Number(rgb[3]), rgb[4] === undefined ? 1 : Number(rgb[4])];
  const hex = /^#([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})$/i.exec(colour.trim());
  if (hex) return [parseInt(hex[1], 16), parseInt(hex[2], 16), parseInt(hex[3], 16), 1];
  return null;
}

function formatColour([r, g, b, a]: Rgba): string {
  return `rgba(${Math.round(r)}, ${Math.round(g)}, ${Math.round(b)}, ${Number(a.toFixed(3))})`;
}

/** Ease-out cubic: quick to react to the pointer, gentle as it lands. */
function ease(t: number): number {
  return 1 - (1 - t) ** 3;
}

function same(a: Rgba, b: Rgba): boolean {
  return a[0] === b[0] && a[1] === b[1] && a[2] === b[2] && a[3] === b[3];
}

export class VisualTween {
  private motions = new Map<string, Motion>();
  private latestStart = Number.NEGATIVE_INFINITY;

  constructor(private readonly durationMs: number) {}

  /** The look to draw `key` with at time `now`, easing toward `target`. */
  resolve(key: string, target: Look, now: number): Look {
    const to = parseColour(target.color);
    if (to === null) return target;
    const motion = this.motions.get(key);
    if (motion === undefined) {
      // First sighting: draw it as-is. Animating in from nothing would flash the whole graph.
      this.motions.set(key, { from: to, to, fromSize: target.size, toSize: target.size, start: Number.NEGATIVE_INFINITY });
      return target;
    }
    if (!same(motion.to, to) || motion.toSize !== target.size) {
      const current = this.at(motion, now);
      motion.from = current.rgba;
      motion.fromSize = current.size;
      motion.to = to;
      motion.toSize = target.size;
      motion.start = now;
      this.latestStart = Math.max(this.latestStart, now);
    }
    const current = this.at(motion, now);
    return { color: formatColour(current.rgba), size: current.size };
  }

  /** The time after which nothing is moving; refresh every frame until then. */
  activeUntil(): number {
    return this.latestStart + this.durationMs;
  }

  /** Forgets every element, e.g. when the graph itself is replaced. */
  clear(): void {
    this.motions.clear();
    this.latestStart = Number.NEGATIVE_INFINITY;
  }

  private at(motion: Motion, now: number): { rgba: Rgba; size: number } {
    const raw = this.durationMs <= 0 ? 1 : (now - motion.start) / this.durationMs;
    const t = ease(Math.max(0, Math.min(1, raw)));
    const mix = (a: number, b: number) => a + (b - a) * t;
    return {
      rgba: [mix(motion.from[0], motion.to[0]), mix(motion.from[1], motion.to[1]), mix(motion.from[2], motion.to[2]), mix(motion.from[3], motion.to[3])],
      size: mix(motion.fromSize, motion.toSize),
    };
  }
}
