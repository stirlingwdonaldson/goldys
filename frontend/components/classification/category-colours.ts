import { SERIES_COLORS } from "@/components/widgets/chart-style";

/**
 * A product with no category yet reads as "nothing here" rather than as a category of
 * its own, so it stays out of the series palette.
 */
export const UNASSIGNED_COLOUR = "hsl(var(--muted-foreground))";

/**
 * A stable colour per category id: the same id always lands on the same series hue, so
 * a category keeps its colour across the scatter, the tree and a reload. Category ids
 * are opaque strings, so the hash is derived from their characters only — no locale, no
 * random seed, no call ordering.
 *
 * The series palette is deliberately small (four hues; see `widgets/chart-style.ts`), so
 * a taxonomy wider than four categories reuses hues. Names, not colour, carry identity:
 * the tooltip, legend and table all spell the category out.
 */
export function categoryColour(categoryId: string | null): string {
  if (categoryId == null || categoryId === "") return UNASSIGNED_COLOUR;
  let hash = 0;
  for (let i = 0; i < categoryId.length; i += 1) {
    hash = (hash * 31 + categoryId.charCodeAt(i)) % 100003;
  }
  return SERIES_COLORS[hash % SERIES_COLORS.length];
}
