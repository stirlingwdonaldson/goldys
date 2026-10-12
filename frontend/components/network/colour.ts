/**
 * Sigma's WebGL programs parse hex and rgb()/rgba() colours but not hsl(), and the design tokens
 * are HSL triplets. Converts `hsl(h s% l%)` (optionally `/ a`) to `rgba(...)`, applying `alpha` on
 * top; anything else passes through untouched.
 */
export function toRgba(colour: string, alpha = 1): string {
  const match = /^hsla?\(\s*([\d.]+)\s+([\d.]+)%\s+([\d.]+)%\s*(?:\/\s*([\d.]+))?\s*\)$/.exec(colour.trim());
  if (!match) return colour;
  const h = Number(match[1]) / 360;
  const s = Number(match[2]) / 100;
  const l = Number(match[3]) / 100;
  const a = (match[4] === undefined ? 1 : Number(match[4])) * alpha;
  const hueToRgb = (p: number, q: number, t: number) => {
    const u = t < 0 ? t + 1 : t > 1 ? t - 1 : t;
    if (u < 1 / 6) return p + (q - p) * 6 * u;
    if (u < 1 / 2) return q;
    if (u < 2 / 3) return p + (q - p) * (2 / 3 - u) * 6;
    return p;
  };
  let r = l;
  let g = l;
  let b = l;
  if (s > 0) {
    const q = l < 0.5 ? l * (1 + s) : l + s - l * s;
    const p = 2 * l - q;
    r = hueToRgb(p, q, h + 1 / 3);
    g = hueToRgb(p, q, h);
    b = hueToRgb(p, q, h - 1 / 3);
  }
  const byte = (v: number) => Math.round(v * 255);
  return `rgba(${byte(r)}, ${byte(g)}, ${byte(b)}, ${Number(a.toFixed(3))})`;
}

/**
 * `colour` blended toward `background` by `amount` (0 = colour, 1 = background), returned opaque.
 * Faint lines and faded nodes use this instead of transparency: WebGL anti-aliasing all but erases
 * thin, low-alpha strokes, while an opaque pale colour renders the same everywhere.
 */
export function mixColour(colour: string, background: string, amount: number): string {
  const parse = (value: string) => /rgba?\((\d+), (\d+), (\d+)/.exec(toRgba(value));
  const a = parse(colour);
  const b = parse(background);
  if (!a || !b) return colour;
  const t = Math.max(0, Math.min(1, amount));
  const channel = (i: number) => Math.round(Number(a[i]) * (1 - t) + Number(b[i]) * t);
  return `rgb(${channel(1)}, ${channel(2)}, ${channel(3)})`;
}
